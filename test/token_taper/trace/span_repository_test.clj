;; SPDX-FileCopyrightText: 2026 Haluan Irsad
;; SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Commercial

(ns token-taper.trace.span-repository-test
  (:require
   [clojure.test :refer [deftest is use-fixtures]]
   [token-taper.db.datasource :as datasource]
   [token-taper.db.jdbc :as jdbc]
   [token-taper.db.migration :as migration]
   [token-taper.db.test-support :as db-support]
   [token-taper.task.test-support :as task-support]
   [token-taper.trace.span-repository :as repo]
   [token-taper.trace.test-support :as support]
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
        (when-not (table-exists? ds "ai_span")
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

(deftest ^:integration create-root-span-and-find-by-id-test
  (let [db (integration-db*)
        {:keys [tenant-id task]} (support/insert-tenant-and-task! db)
        task-id (:task/id task)
        created (repo/create-span!
                 db
                 (support/sample-create-input
                  tenant-id task-id
                  :external-span-id "ext-root-1"
                  :name "classify"
                  :span-type :agent_step
                  :metadata {:step 1}))
        found (repo/find-span-by-id db (:span/id created))]
    (try
      (is (= :started (:span/status created)))
      (is (nil? (:span/finished-at created)))
      (is (= (:span/id created) (:span/id found)))
      (is (= "ext-root-1" (:span/external-id found)))
      (is (= "classify" (:span/name found)))
      (is (= 1 (get-in found [:span/metadata :step])))
      (finally
        (task-support/delete-task! db task-id)
        (task-support/delete-tenant! db tenant-id)))))

(deftest ^:integration create-child-span-test
  (let [db (integration-db*)
        {:keys [tenant-id task]} (support/insert-tenant-and-task! db)
        task-id (:task/id task)
        parent (repo/create-span!
                db
                (support/sample-create-input tenant-id task-id :name "parent"))
        child (repo/create-span!
               db
               (support/sample-create-input
                tenant-id task-id
                :parent-span-id (:span/id parent)
                :name "child"))
        children (repo/find-child-spans db (:span/id parent))]
    (try
      (is (= (:span/id parent) (:span/parent-id child)))
      (is (= 1 (count children)))
      (is (= (:span/id child) (:span/id (first children))))
      (finally
        (task-support/delete-task! db task-id)
        (task-support/delete-tenant! db tenant-id)))))

(deftest ^:integration find-spans-by-task-id-order-test
  (let [db (integration-db*)
        {:keys [tenant-id task]} (support/insert-tenant-and-task! db)
        task-id (:task/id task)
        t0 (Instant/parse "2026-01-01T00:00:00Z")
        t1 (Instant/parse "2026-01-01T00:00:01Z")
        s1 (repo/create-span!
            db
            (support/sample-create-input tenant-id task-id
                                         :name "first"
                                         :started-at t1))
        s0 (repo/create-span!
            db
            (support/sample-create-input tenant-id task-id
                                         :name "second"
                                         :started-at t0))
        spans (repo/find-spans-by-task-id db task-id)]
    (try
      (is (= 2 (count spans)))
      (is (= (:span/id s0) (:span/id (first spans))))
      (is (= (:span/id s1) (:span/id (second spans))))
      (finally
        (task-support/delete-task! db task-id)
        (task-support/delete-tenant! db tenant-id)))))

(deftest ^:integration find-by-external-id-test
  (let [db (integration-db*)
        {:keys [tenant-id task]} (support/insert-tenant-and-task! db)
        task-id (:task/id task)
        created (repo/create-span!
                 db
                 (support/sample-create-input tenant-id task-id
                                              :external-span-id "ext-lookup"))]
    (try
      (is (= (:span/id created)
             (:span/id (repo/find-span-by-external-id db tenant-id "ext-lookup"))))
      (finally
        (task-support/delete-task! db task-id)
        (task-support/delete-tenant! db tenant-id)))))

(deftest ^:integration duplicate-external-id-same-tenant-test
  (let [db (integration-db*)
        {:keys [tenant-id task]} (support/insert-tenant-and-task! db)
        task-id (:task/id task)
        created (repo/create-span!
                 db
                 (support/sample-create-input tenant-id task-id
                                              :external-span-id "dup-ext"))]
    (try
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Duplicate external_span_id"
                            (repo/create-span!
                             db
                             (support/sample-create-input tenant-id task-id
                                                          :external-span-id "dup-ext"))))
      (finally
        (task-support/delete-task! db task-id)
        (task-support/delete-tenant! db tenant-id)))))

(deftest ^:integration same-external-id-different-tenants-test
  (let [db (integration-db*)
        setup-a (support/insert-tenant-and-task! db)
        setup-b (support/insert-tenant-and-task! db)
        tenant-a (:tenant-id setup-a)
        tenant-b (:tenant-id setup-b)
        task-a-id (:task/id (:task setup-a))
        task-b-id (:task/id (:task setup-b))
        span-a (repo/create-span!
                db
                (support/sample-create-input tenant-a task-a-id
                                             :external-span-id "shared-ext"))
        span-b (repo/create-span!
                db
                (support/sample-create-input tenant-b task-b-id
                                             :external-span-id "shared-ext"))]
    (try
      (is (not= (:span/id span-a) (:span/id span-b)))
      (finally
        (task-support/delete-task! db task-a-id)
        (task-support/delete-task! db task-b-id)
        (task-support/delete-tenant! db tenant-a)
        (task-support/delete-tenant! db tenant-b)))))

(deftest ^:integration finish-span-updates-status-test
  (let [db (integration-db*)
        {:keys [tenant-id task]} (support/insert-tenant-and-task! db)
        task-id (:task/id task)
        created (repo/create-span!
                 db
                 (support/sample-create-input tenant-id task-id))
        finished-at (Instant/now)
        finished (repo/finish-span!
                  db
                  (:span/id created)
                  (support/sample-finish-input
                   :status :finished
                   :finished-at finished-at
                   :metadata {:result "ok"}))]
    (try
      (is (= :finished (:span/status finished)))
      (is (some? (:span/finished-at finished)))
      (is (= "ok" (:result (:span/metadata finished))))
      (is (>= (.compareTo (:span/updated-at finished)
                          (:span/created-at finished))
              0))
      (finally
        (task-support/delete-task! db task-id)
        (task-support/delete-tenant! db tenant-id)))))

(deftest ^:integration finish-terminal-span-conflicts-test
  (let [db (integration-db*)
        {:keys [tenant-id task]} (support/insert-tenant-and-task! db)
        task-id (:task/id task)
        created (repo/create-span!
                 db
                 (support/sample-create-input tenant-id task-id))
        _ (repo/finish-span!
           db
           (:span/id created)
           (support/sample-finish-input :status :finished))]
    (try
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"already terminal|not in started"
                            (repo/finish-span!
                             db
                             (:span/id created)
                             (support/sample-finish-input :status :failed))))
      (finally
        (task-support/delete-task! db task-id)
        (task-support/delete-tenant! db tenant-id)))))

(deftest ^:integration finish-not-found-test
  (let [db (integration-db*)]
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"not found"
                          (repo/finish-span!
                           db
                           (UUID/randomUUID)
                           (support/sample-finish-input))))))

(deftest ^:integration parent-from-other-task-rejected-test
  (let [db (integration-db*)
        setup-a (support/insert-tenant-and-task! db)
        setup-b (support/insert-tenant-and-task! db)
        tenant-a (:tenant-id setup-a)
        tenant-b (:tenant-id setup-b)
        task-a-id (:task/id (:task setup-a))
        task-b-id (:task/id (:task setup-b))
        parent-on-b (repo/create-span!
                     db
                     (support/sample-create-input tenant-b task-b-id :name "parent-b"))]
    (try
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"another task"
                            (repo/create-span!
                             db
                             (support/sample-create-input
                              tenant-a task-a-id
                              :parent-span-id (:span/id parent-on-b)))))
      (finally
        (task-support/delete-task! db task-a-id)
        (task-support/delete-task! db task-b-id)
        (task-support/delete-tenant! db tenant-a)
        (task-support/delete-tenant! db tenant-b)))))

(deftest ^:integration create-span-task-not-found-test
  (let [db (integration-db*)
        tenant-id (task-support/insert-tenant! db)]
    (try
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Task not found"
                            (repo/create-span!
                             db
                             (support/sample-create-input tenant-id (UUID/randomUUID)))))
      (finally
        (task-support/delete-tenant! db tenant-id)))))

(deftest ^:integration delete-task-cascades-spans-test
  (let [db (integration-db*)
        {:keys [tenant-id task]} (support/insert-tenant-and-task! db)
        task-id (:task/id task)
        _ (repo/create-span!
           db
           (support/sample-create-input tenant-id task-id :name "root"))
        _ (repo/create-span!
           db
           (support/sample-create-input tenant-id task-id :name "root2"))]
    (try
      (is (= 2 (support/span-count-for-task db task-id)))
      (task-support/delete-task! db task-id)
      (is (= 0 (support/span-count-for-task db task-id)))
      (finally
        (task-support/delete-tenant! db tenant-id)))))
