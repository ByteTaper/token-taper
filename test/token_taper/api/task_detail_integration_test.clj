;; SPDX-FileCopyrightText: 2026 Haluan Irsad
;; SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Commercial

(ns token-taper.api.task-detail-integration-test
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

(defn- get-task [task-id]
  ((app) (mock/request :get (str "/v1/tasks/" task-id))))

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

(deftest ^:integration get-task-detail-after-start-test
  (let [db (:datasource @integration-state)
        tenant-id (task-support/insert-tenant! db)
        started (post-start {:tenant_id (str tenant-id)
                             :external_task_id "detail-ext"
                             :workflow "wf"
                             :metadata {:source "test"}})
        task-id (:task_id (json-body started))]
    (try
      (let [response (get-task task-id)
            body (json-body response)]
        (is (= 201 (:status started)))
        (is (= 200 (:status response)))
        (is (= task-id (:task_id body)))
        (is (= "started" (:status body)))
        (is (= "detail-ext" (:external_task_id body)))
        (is (= "test" (get-in body [:metadata :source])))
        (is (some? (:created_at body)))
        (is (some? (:updated_at body))))
      (finally
        (task-support/delete-task! db (UUID/fromString task-id))
        (task-support/delete-tenant! db tenant-id)))))

(deftest ^:integration get-task-detail-not-found-test
  (let [missing (UUID/randomUUID)
        response (get-task (str missing))
        body (json-body response)]
    (is (= 404 (:status response)))
    (is (= "task_not_found" (:error body)))))
