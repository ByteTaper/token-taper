;; SPDX-FileCopyrightText: 2026 Haluan Irsad
;; SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Commercial

(ns token-taper.task.repository-test
  (:require
   [clojure.test :refer [deftest is use-fixtures]]
   [token-taper.db.datasource :as datasource]
   [token-taper.db.jdbc :as jdbc]
   [token-taper.db.migration :as migration]
   [token-taper.db.test-support :as db-support]
   [token-taper.task.repository :as repo]
   [token-taper.task.test-support :as support]
   [token-taper.test-support.logging :as log-support]
   [token-taper.system.config :as config])
  (:import
   [java.time Instant]
   [java.util UUID]))

(def ^:private integration-db
  (atom nil))

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

(defn- ensure-integration-db!
  []
  (when (db-support/integration-db-available?)
    (when-not @integration-db
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
        (reset! integration-db {:datasource ds})))))

(defn- integration-db*
  []
  (:datasource @integration-db))

(defn integration-fixture
  [f]
  (if (db-support/integration-db-available?)
    (try
      (ensure-integration-db!)
      (f)
      (finally
        (when-let [ds (:datasource @integration-db)]
          (datasource/close-datasource! ds)
          (reset! integration-db nil))))
    (f)))

(use-fixtures :once integration-fixture)

(deftest ^:integration create-and-find-by-id-test
  (let [db (integration-db*)
        tenant-id (support/insert-tenant! db)
        created (repo/create-task!
                 db
                 (support/sample-create-input
                  tenant-id
                  :external-task-id "ext-create-1"
                  :workflow "wf"
                  :task-type "agentic_workflow"
                  :metadata {:source "test"}))
        found (repo/find-task-by-id db (:task/id created))]
    (try
      (is (= :started (:task/status created)))
      (is (nil? (:task/finished-at created)))
      (is (= (:task/id created) (:task/id found)))
      (is (= "ext-create-1" (:task/external-id found)))
      (is (= "test" (get-in found [:task/metadata :source])))
      (finally
        (support/delete-task! db (:task/id created))
        (support/delete-tenant! db tenant-id)))))

(deftest ^:integration find-by-external-id-test
  (let [db (integration-db*)
        tenant-id (support/insert-tenant! db)
        created (repo/create-task!
                 db
                 (support/sample-create-input tenant-id
                                            :external-task-id "ext-lookup"))]
    (try
      (is (= (:task/id created)
             (:task/id (repo/find-task-by-external-id db tenant-id "ext-lookup"))))
      (finally
        (support/delete-task! db (:task/id created))
        (support/delete-tenant! db tenant-id)))))

(deftest ^:integration duplicate-external-id-same-tenant-test
  (let [db (integration-db*)
        tenant-id (support/insert-tenant! db)
        created (repo/create-task!
                 db
                 (support/sample-create-input tenant-id
                                              :external-task-id "dup-ext"))]
    (try
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Duplicate external_task_id"
                            (repo/create-task!
                             db
                             (support/sample-create-input tenant-id
                                                        :external-task-id "dup-ext"))))
      (finally
        (support/delete-task! db (:task/id created))
        (support/delete-tenant! db tenant-id)))))

(deftest ^:integration same-external-id-different-tenants-test
  (let [db (integration-db*)
        tenant-a (support/insert-tenant! db)
        tenant-b (support/insert-tenant! db)
        task-a (repo/create-task!
                db
                (support/sample-create-input tenant-a
                                             :external-task-id "shared-ext"))
        task-b (repo/create-task!
                db
                (support/sample-create-input tenant-b
                                             :external-task-id "shared-ext"))]
    (try
      (is (not= (:task/id task-a) (:task/id task-b)))
      (finally
        (support/delete-task! db (:task/id task-a))
        (support/delete-task! db (:task/id task-b))
        (support/delete-tenant! db tenant-a)
        (support/delete-tenant! db tenant-b)))))

(deftest ^:integration finish-task-updates-status-test
  (let [db (integration-db*)
        tenant-id (support/insert-tenant! db)
        created (repo/create-task!
                 db
                 (support/sample-create-input tenant-id))
        finished-at (Instant/now)
        finished (repo/finish-task!
                  db
                  (:task/id created)
                  (support/sample-finish-input
                   :status :finished
                   :finished-at finished-at
                   :metadata {:result "ok"}))]
    (try
      (is (= :finished (:task/status finished)))
      (is (some? (:task/finished-at finished)))
      (is (= "ok" (:result (:task/metadata finished))))
      (is (>= (.compareTo (:task/updated-at finished)
                           (:task/created-at finished))
              0))
      (finally
        (support/delete-task! db (:task/id created))
        (support/delete-tenant! db tenant-id)))))

(deftest ^:integration finish-terminal-task-conflicts-test
  (let [db (integration-db*)
        tenant-id (support/insert-tenant! db)
        created (repo/create-task!
                 db
                 (support/sample-create-input tenant-id))
        _ (repo/finish-task!
           db
           (:task/id created)
           (support/sample-finish-input :status :finished))]
    (try
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"already terminal|not in started"
                            (repo/finish-task!
                             db
                             (:task/id created)
                             (support/sample-finish-input :status :failed))))
      (finally
        (support/delete-task! db (:task/id created))
        (support/delete-tenant! db tenant-id)))))

(deftest ^:integration finish-not-found-test
  (let [db (integration-db*)]
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"not found"
                          (repo/finish-task!
                           db
                           (UUID/randomUUID)
                           (support/sample-finish-input))))))
