;; SPDX-FileCopyrightText: 2026 Haluan Irsad
;; SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Commercial

(ns token-taper.api.task-finish-integration-test
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

(defn- post-finish [task-id body]
  ((app) (-> (mock/request :post (str "/v1/tasks/" task-id "/finish"))
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

(defn- start-task!
  [db tenant-id]
  (let [response (post-start {:tenant_id (str tenant-id)
                              :external_task_id (str "finish-" (UUID/randomUUID))
                              :metadata {:environment "dev"}})
        body (json-body response)]
    (is (= 201 (:status response)))
    (UUID/fromString (:task_id body))))

(deftest ^:integration finish-task-terminal-statuses-test
  (let [db (:datasource @integration-state)
        tenant-id (task-support/insert-tenant! db)]
    (try
      (doseq [status ["finished" "failed" "cancelled"]]
        (let [task-id (start-task! db tenant-id)
              response (post-finish task-id {:status status})
              body (json-body response)
              found (task-repo/find-task-by-id db task-id)]
          (try
            (is (= 200 (:status response)))
            (is (= status (:status body)))
            (is (= (keyword status) (:task/status found)))
            (is (some? (:task/finished-at found)))
            (finally
              (task-support/delete-task! db task-id)))))
      (finally
        (task-support/delete-tenant! db tenant-id)))))

(deftest ^:integration finish-task-metadata-merge-test
  (let [db (:datasource @integration-state)
        tenant-id (task-support/insert-tenant! db)
        task-id (start-task! db tenant-id)
        response (post-finish task-id {:status "finished"
                                       :metadata {:result "ok"}})
        body (json-body response)
        found (task-repo/find-task-by-id db task-id)]
    (try
      (is (= 200 (:status response)))
      (is (= "dev" (get-in body [:metadata :environment])))
      (is (= "ok" (get-in body [:metadata :result])))
      (is (= "dev" (get-in found [:task/metadata :environment])))
      (is (= "ok" (get-in found [:task/metadata :result])))
      (finally
        (task-support/delete-task! db task-id)
        (task-support/delete-tenant! db tenant-id)))))

(deftest ^:integration finish-task-not-found-404-test
  (let [missing-id (UUID/randomUUID)
        response (post-finish missing-id {:status "finished"})
        body (json-body response)]
    (is (= 404 (:status response)))
    (is (= "task_not_found" (:error body)))))

(deftest ^:integration finish-task-twice-409-test
  (let [db (:datasource @integration-state)
        tenant-id (task-support/insert-tenant! db)
        task-id (start-task! db tenant-id)]
    (try
      (is (= 200 (:status (post-finish task-id {:status "finished"}))))
      (let [second (post-finish task-id {:status "finished"})
            body (json-body second)]
        (is (= 409 (:status second)))
        (is (= "task_conflict" (:error body))))
      (finally
        (task-support/delete-task! db task-id)
        (task-support/delete-tenant! db tenant-id)))))
