;; SPDX-FileCopyrightText: 2026 Haluan Irsad
;; SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Commercial

(ns token-taper.api.task-trace-integration-test
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
   [token-taper.task.test-support :as task-support]
   [token-taper.trace.span-repository :as span-repo]
   [token-taper.trace.test-support :as span-support]
   [token-taper.test-support.handler-fixtures :as fixtures]
   [token-taper.test-support.logging :as log-support]
   [token-taper.system.config :as config])
  (:import
   [java.time Instant]
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

(defn- post-start [body]
  ((app) (-> (mock/request :post "/v1/tasks/start")
             (mock/content-type "application/json")
             (mock/json-body body))))

(defn- get-trace [task-id]
  ((app) (mock/request :get (str "/v1/tasks/" task-id "/trace"))))

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

(deftest ^:integration get-trace-empty-test
  (let [db (:datasource @integration-state)
        tenant-id (task-support/insert-tenant! db)
        started (post-start {:tenant_id (str tenant-id)})
        task-id (:task_id (json-body started))]
    (try
      (let [response (get-trace task-id)
            body (json-body response)]
        (is (= 200 (:status response)))
        (is (= task-id (get-in body [:task :task_id])))
        (is (= [] (:spans body)))
        (is (= [] (:events body))))
      (finally
        (task-support/delete-task! db (UUID/fromString task-id))
        (task-support/delete-tenant! db tenant-id)))))

(deftest ^:integration get-trace-with-spans-and-events-test
  (let [db (:datasource @integration-state)
        tenant-id (task-support/insert-tenant! db)
        started (post-start {:tenant_id (str tenant-id)})
        task-id (UUID/fromString (:task_id (json-body started)))
        early (Instant/parse "2026-06-05T10:00:00Z")
        late (Instant/parse "2026-06-05T10:00:05Z")
        span-a (span-repo/create-span!
                db
                (span-support/sample-create-input tenant-id task-id
                                                  :name "first"
                                                  :span-type :agent_step
                                                  :started-at early))
        span-b (span-repo/create-span!
                db
                (span-support/sample-create-input tenant-id task-id
                                                  :name "second"
                                                  :span-type :tool_call
                                                  :started-at late))
        event-a (event-repo/create-event!
                 db
                 (event-support/sample-llm-call-input tenant-id task-id
                                                    :span-id (:span/id span-a)
                                                    :occurred-at early))
        event-b (event-repo/create-event!
                 db
                 (event-support/sample-tool-call-input tenant-id task-id
                                                       :span-id (:span/id span-b)
                                                       :occurred-at late))]
    (try
      (let [body (json-body (get-trace (str task-id)))
            spans (:spans body)
            events (:events body)]
        (is (= 2 (count spans)))
        (is (= "first" (:name (first spans))))
        (is (= "second" (:name (second spans))))
        (is (= 2 (count events)))
        (is (= "llm_call" (:event_type (first events))))
        (is (= (str (:span/id span-a)) (:span_id (first events))))
        (is (= (str (:span/id span-b)) (:span_id (second events)))))
      (finally
        (event-support/delete-event! db (:event/id event-a))
        (event-support/delete-event! db (:event/id event-b))
        (span-support/delete-span! db (:span/id span-a))
        (span-support/delete-span! db (:span/id span-b))
        (task-support/delete-task! db task-id)
        (task-support/delete-tenant! db tenant-id)))))
