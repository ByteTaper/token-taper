;; SPDX-FileCopyrightText: 2026 Haluan Irsad
;; SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Commercial

(ns token-taper.api.llm-call-event-integration-test
  (:require
   [clojure.test :refer [deftest is use-fixtures]]
   [jsonista.core :as json]
   [ring.mock.request :as mock]
   [token-taper.api.server :as server]
   [token-taper.db.datasource :as datasource]
   [token-taper.db.jdbc :as jdbc]
   [token-taper.db.migration :as migration]
   [token-taper.db.test-support :as db-support]
   [token-taper.event.repository :as event-repo]
   [token-taper.event.test-support :as event-support]
   [token-taper.observability.metrics :as metrics]
   [token-taper.task.repository :as task-repo]
   [token-taper.task.test-support :as task-support]
   [token-taper.trace.span-repository :as span-repo]
   [token-taper.trace.test-support :as span-support]
   [token-taper.test-support.handler-fixtures :as fixtures]
   [token-taper.test-support.logging :as log-support]
   [token-taper.system.config :as config])
  (:import
   [java.util UUID]))

(def ^:private mapper (json/object-mapper {:decode-key-fn keyword}))

(def ^:private integration-state (atom nil))

(defn- table-exists?
  [ds table-name]
  (boolean
   (:exists
    (jdbc/execute-one!
     ds
     ["SELECT EXISTS (
         SELECT FROM information_schema.tables
         WHERE table_schema = 'public' AND table_name = ?
       ) AS exists"
      table-name]))))

(defn- ensure-db!
  []
  (when (db-support/integration-db-available?)
    (when-not @integration-state
      (let [ds-cfg (db-support/load-test-datasource-config)
            mig-cfg (get (config/load-config "resources/config.test.edn")
                         :token-taper.db/migration)
            logger (log-support/test-logger)
            ds (datasource/make-datasource ds-cfg)]
        (when-not (table-exists? ds "ai_event")
          (migration/migrate!
           {:datasource ds
            :migration-dir (:migration-dir mig-cfg)
            :logger logger}))
        (reset! integration-state {:datasource ds :logger logger})))))

(defn- app []
  (let [{:keys [datasource logger]} @integration-state
        health-system (fixtures/default-health-system datasource)]
    (server/handler
     {:system-info (fixtures/system-info-payload (:app health-system) health-system)
      :health-system (assoc health-system :logger logger)
      :metrics (assoc (metrics/create-registry
                       {:app (:app health-system)
                        :datasource datasource
                        :health-config (:health-config health-system)
                        :git-sha "unknown"})
                      :logger logger)
      :datasource datasource
      :logger logger})))

(defn- json-body [response]
  (json/read-value (:body response) mapper))

(defn- post-llm-call [body]
  ((app) (-> (mock/request :post "/v1/events/llm-call")
             (mock/content-type "application/json")
             (mock/json-body body))))

(defn- wire-body
  [tenant-id task-id & {:keys [span-id external-event-id]}]
  (cond-> {:tenant_id (str tenant-id)
           :task_id (str task-id)
           :provider "anthropic"
           :model "claude-sonnet"
           :input_tokens 3000
           :output_tokens 700
           :latency_ms 3400
           :status "success"
           :metadata {:source "integration"}}
    span-id (assoc :span_id (str span-id))
    external-event-id (assoc :external_event_id external-event-id)))

(defn integration-fixture
  [f]
  (if (db-support/integration-db-available?)
    (try
      (ensure-db!)
      (f)
      (finally
        (when-let [ds (:datasource @integration-state)]
          (datasource/close-datasource! ds)
          (reset! integration-state nil))))
    (f)))

(use-fixtures :once integration-fixture)

(deftest ^:integration ingest-llm-call-task-only-test
  (let [db (:datasource @integration-state)
        {:keys [tenant-id task-id]} (event-support/insert-tenant-task-span! db)]
    (try
      (let [response (post-llm-call (wire-body tenant-id task-id :external-event-id "llm-ext-1"))
            body (json-body response)
            event-id (UUID/fromString (:event_id body))
            found (event-repo/find-event-by-id db event-id)]
        (is (= 201 (:status response)))
        (is (= "llm_call" (:event_type body)))
        (is (= :llm_call (:event/type found)))
        (is (= task-id (:task/id found)))
        (is (nil? (:span/id found))))
      (finally
        (doseq [e (event-repo/find-events-by-task-id db task-id)]
          (event-support/delete-event! db (:event/id e)))
        (task-support/delete-task! db task-id)
        (task-support/delete-tenant! db tenant-id)))))

(deftest ^:integration ingest-llm-call-with-span-test
  (let [db (:datasource @integration-state)
        {:keys [tenant-id task-id span]} (event-support/insert-tenant-task-span! db)
        span-id (:span/id span)]
    (try
      (let [response (post-llm-call (wire-body tenant-id task-id
                                               :span-id span-id
                                               :external-event-id "llm-span-ext"))
            body (json-body response)
            found (event-repo/find-event-by-id db (UUID/fromString (:event_id body)))]
        (is (= 201 (:status response)))
        (is (= (str span-id) (:span_id body)))
        (is (= span-id (:span/id found))))
      (finally
        (doseq [e (event-repo/find-events-by-task-id db task-id)]
          (event-support/delete-event! db (:event/id e)))
        (task-support/delete-task! db task-id)
        (task-support/delete-tenant! db tenant-id)))))

(deftest ^:integration duplicate-external-event-id-409-test
  (let [db (:datasource @integration-state)
        {:keys [tenant-id task-id]} (event-support/insert-tenant-task-span! db)
        body (wire-body tenant-id task-id :external-event-id "dup-llm")]
    (try
      (is (= 201 (:status (post-llm-call body))))
      (is (= 409 (:status (post-llm-call body))))
      (finally
        (doseq [e (event-repo/find-events-by-task-id db task-id)]
          (event-support/delete-event! db (:event/id e)))
        (task-support/delete-task! db task-id)
        (task-support/delete-tenant! db tenant-id)))))

(deftest ^:integration unknown-task-404-test
  (let [tenant-id (UUID/randomUUID)
        task-id (UUID/randomUUID)
        response (post-llm-call (wire-body tenant-id task-id))
        body (json-body response)]
    (is (= 404 (:status response)))
    (is (= "task_not_found" (:error body)))))

(deftest ^:integration ingest-llm-call-span-wrong-task-400-test
  (let [db (:datasource @integration-state)
        tenant-id (task-support/insert-tenant! db)
        task-a (task-repo/create-task! db (task-support/sample-create-input tenant-id))
        task-b (task-repo/create-task! db (task-support/sample-create-input tenant-id))
        task-a-id (:task/id task-a)
        task-b-id (:task/id task-b)
        span (span-repo/create-span!
              db
              (span-support/sample-create-input tenant-id task-a-id :name "task-a-span"))]
    (try
      (let [response (post-llm-call (wire-body tenant-id task-b-id
                                               :span-id (:span/id span)))
            body (json-body response)]
        (is (= 400 (:status response)))
        (is (= "validation_error" (:error body))))
      (finally
        (span-support/delete-span! db (:span/id span))
        (task-support/delete-task! db task-a-id)
        (task-support/delete-task! db task-b-id)
        (task-support/delete-tenant! db tenant-id)))))

(deftest ^:integration same-external-event-id-different-tenants-201-test
  (let [db (:datasource @integration-state)
        setup-a (event-support/insert-tenant-task-span! db)
        setup-b (event-support/insert-tenant-task-span! db)
        tenant-a (:tenant-id setup-a)
        tenant-b (:tenant-id setup-b)
        task-a-id (:task-id setup-a)
        task-b-id (:task-id setup-b)
        ext-id "shared-cross-tenant-llm"]
    (try
      (is (= 201 (:status (post-llm-call (wire-body tenant-a task-a-id
                                                    :external-event-id ext-id)))))
      (is (= 201 (:status (post-llm-call (wire-body tenant-b task-b-id
                                                    :external-event-id ext-id)))))
      (finally
        (doseq [task-id [task-a-id task-b-id]]
          (doseq [e (event-repo/find-events-by-task-id db task-id)]
            (event-support/delete-event! db (:event/id e)))
          (task-support/delete-task! db task-id))
        (task-support/delete-tenant! db tenant-a)
        (task-support/delete-tenant! db tenant-b)))))
