;; SPDX-FileCopyrightText: 2026 Haluan Irsad
;; SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Commercial

(ns token-taper.api.task-start-integration-test
  (:require
   [clojure.test :refer [deftest is use-fixtures]]
   [jsonista.core :as json]
   [ring.mock.request :as mock]
   [token-taper.api.server :as server]
   [token-taper.db.datasource :as datasource]
   [token-taper.db.jdbc :as jdbc]
   [token-taper.db.migration :as migration]
   [token-taper.db.test-support :as db-support]
   [token-taper.observability.metrics :as metrics]
   [token-taper.task.repository :as task-repo]
   [token-taper.task.test-support :as task-support]
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
        (when-not (table-exists? ds "ai_task")
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

(defn- post-start [body]
  ((app) (-> (mock/request :post "/v1/tasks/start")
             (mock/content-type "application/json")
             (mock/json-body body))))

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

(deftest ^:integration start-task-persists-row-test
  (let [db (:datasource @integration-state)
        tenant-id (task-support/insert-tenant! db)
        response (post-start {:tenant_id (str tenant-id)
                              :external_task_id "api-ext-1"
                              :workflow "wf"
                              :task_type "agentic_workflow"
                              :metadata {:source "test"}})
        body (json-body response)
        task-id (UUID/fromString (:task_id body))
        found (task-repo/find-task-by-id db task-id)]
    (try
      (is (= 201 (:status response)))
      (is (= :started (:task/status found)))
      (is (nil? (:task/finished-at found)))
      (is (= "test" (get-in found [:task/metadata :source])))
      (is (= "api-ext-1" (:task/external-id found)))
      (finally
        (task-support/delete-task! db task-id)
        (task-support/delete-tenant! db tenant-id)))))

(deftest ^:integration duplicate-external-id-409-test
  (let [db (:datasource @integration-state)
        tenant-id (task-support/insert-tenant! db)
        created (post-start {:tenant_id (str tenant-id)
                             :external_task_id "dup-api"})
        task-id (UUID/fromString (:task_id (json-body created)))]
    (try
      (is (= 409 (:status (post-start {:tenant_id (str tenant-id)
                                      :external_task_id "dup-api"}))))
      (finally
        (task-support/delete-task! db task-id)
        (task-support/delete-tenant! db tenant-id)))))

(deftest ^:integration same-external-id-different-tenants-test
  (let [db (:datasource @integration-state)
        tenant-a (task-support/insert-tenant! db)
        tenant-b (task-support/insert-tenant! db)
        resp-a (post-start {:tenant_id (str tenant-a) :external_task_id "shared"})
        resp-b (post-start {:tenant_id (str tenant-b) :external_task_id "shared"})
        id-a (UUID/fromString (:task_id (json-body resp-a)))
        id-b (UUID/fromString (:task_id (json-body resp-b)))]
    (try
      (is (not= id-a id-b))
      (is (= 201 (:status resp-a)))
      (is (= 201 (:status resp-b)))
      (finally
        (task-support/delete-task! db id-a)
        (task-support/delete-task! db id-b)
        (task-support/delete-tenant! db tenant-a)
        (task-support/delete-tenant! db tenant-b)))))
