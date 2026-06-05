;; SPDX-FileCopyrightText: 2026 Haluan Irsad
;; SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Commercial

(ns token-taper.event.repository-test
  (:require
   [clojure.test :refer [deftest is use-fixtures]]
   [token-taper.db.datasource :as datasource]
   [token-taper.db.jdbc :as jdbc]
   [token-taper.db.migration :as migration]
   [token-taper.db.test-support :as db-support]
   [token-taper.event.repository :as repo]
   [token-taper.event.test-support :as support]
   [token-taper.task.test-support :as task-support]
   [token-taper.trace.span-repository :as span-repo]
   [token-taper.trace.test-support :as span-support]
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
        (when-not (table-exists? ds "ai_event")
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

(deftest ^:integration create-llm-call-and-find-by-id-test
  (let [db (integration-db*)
        {:keys [tenant-id task-id span]} (support/insert-tenant-task-span! db)
        created (repo/create-event!
                 db
                 (support/sample-llm-call-input tenant-id task-id
                                                :span-id (:span/id span)
                                                :external-event-id "ext-llm-1"))
        found (repo/find-event-by-id db (:event/id created))]
    (try
      (is (= :llm_call (:event/type found)))
      (is (= "anthropic" (:event/provider found)))
      (is (= 3000 (:event/input-tokens found)))
      (is (= (:span/id span) (:span/id found)))
      (finally
        (task-support/delete-task! db task-id)
        (task-support/delete-tenant! db tenant-id)))))

(deftest ^:integration create-tool-call-event-test
  (let [db (integration-db*)
        {:keys [tenant-id task-id]} (support/insert-tenant-task-span! db)
        created (repo/create-event!
                 db
                 (support/sample-tool-call-input tenant-id task-id))]
    (try
      (is (= :tool_call (:event/type created)))
      (is (= "vector_search" (:event/tool-name created)))
      (finally
        (task-support/delete-task! db task-id)
        (task-support/delete-tenant! db tenant-id)))))

(deftest ^:integration create-retry-and-cache-events-test
  (let [db (integration-db*)
        {:keys [tenant-id task-id]} (support/insert-tenant-task-span! db)
        retry (repo/create-event!
               db
               (support/sample-retry-input tenant-id task-id))
        cache (repo/create-event!
               db
               (support/sample-cache-input tenant-id task-id :cache-hit true))]
    (try
      (is (= :retry (:event/type retry)))
      (is (= "invalid_json" (get-in retry [:event/metadata :reason])))
      (is (= :cache (:event/type cache)))
      (is (true? (:event/cache-hit cache)))
      (finally
        (task-support/delete-task! db task-id)
        (task-support/delete-tenant! db tenant-id)))))

(deftest ^:integration find-by-external-id-test
  (let [db (integration-db*)
        {:keys [tenant-id task-id]} (support/insert-tenant-task-span! db)
        created (repo/create-event!
                 db
                 (support/sample-llm-call-input tenant-id task-id
                                                :external-event-id "ext-lookup"))]
    (try
      (is (= (:event/id created)
             (:event/id (repo/find-event-by-external-id db tenant-id "ext-lookup"))))
      (finally
        (task-support/delete-task! db task-id)
        (task-support/delete-tenant! db tenant-id)))))

(deftest ^:integration find-events-by-task-id-order-test
  (let [db (integration-db*)
        {:keys [tenant-id task-id]} (support/insert-tenant-task-span! db)
        t0 (Instant/parse "2026-01-01T00:00:00Z")
        t1 (Instant/parse "2026-01-01T00:00:01Z")
        e1 (repo/create-event!
            db
            (support/sample-cache-input tenant-id task-id :occurred-at t1))
        e0 (repo/create-event!
            db
            (support/sample-cache-input tenant-id task-id :occurred-at t0))
        events (repo/find-events-by-task-id db task-id)]
    (try
      (is (= 2 (count events)))
      (is (= (:event/id e0) (:event/id (first events))))
      (is (= (:event/id e1) (:event/id (second events))))
      (finally
        (task-support/delete-task! db task-id)
        (task-support/delete-tenant! db tenant-id)))))

(deftest ^:integration find-events-by-span-id-test
  (let [db (integration-db*)
        {:keys [tenant-id task-id span]} (support/insert-tenant-task-span! db)
        _ (repo/create-event!
           db
           (support/sample-cache-input tenant-id task-id))
        in-span (repo/create-event!
                 db
                 (support/sample-cache-input tenant-id task-id
                                             :span-id (:span/id span)))
        events (repo/find-events-by-span-id db (:span/id span))]
    (try
      (is (= 1 (count events)))
      (is (= (:event/id in-span) (:event/id (first events))))
      (finally
        (task-support/delete-task! db task-id)
        (task-support/delete-tenant! db tenant-id)))))

(deftest ^:integration find-events-by-task-id-and-type-test
  (let [db (integration-db*)
        {:keys [tenant-id task-id]} (support/insert-tenant-task-span! db)
        _ (repo/create-event!
           db
           (support/sample-tool-call-input tenant-id task-id))
        llm (repo/create-event!
             db
             (support/sample-llm-call-input tenant-id task-id))
        llm-events (repo/find-events-by-task-id-and-type db task-id :llm_call)]
    (try
      (is (= 1 (count llm-events)))
      (is (= (:event/id llm) (:event/id (first llm-events))))
      (finally
        (task-support/delete-task! db task-id)
        (task-support/delete-tenant! db tenant-id)))))

(deftest ^:integration duplicate-external-id-same-tenant-test
  (let [db (integration-db*)
        {:keys [tenant-id task-id]} (support/insert-tenant-task-span! db)
        _ (repo/create-event!
           db
           (support/sample-llm-call-input tenant-id task-id
                                          :external-event-id "dup-ext"))]
    (try
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Duplicate external_event_id"
                            (repo/create-event!
                             db
                             (support/sample-llm-call-input tenant-id task-id
                                                            :external-event-id "dup-ext"))))
      (finally
        (task-support/delete-task! db task-id)
        (task-support/delete-tenant! db tenant-id)))))

(deftest ^:integration same-external-id-different-tenants-test
  (let [db (integration-db*)
        setup-a (support/insert-tenant-task-span! db)
        setup-b (support/insert-tenant-task-span! db)
        tenant-a (:tenant-id setup-a)
        tenant-b (:tenant-id setup-b)
        task-a-id (:task-id setup-a)
        task-b-id (:task-id setup-b)
        evt-a (repo/create-event!
               db
               (support/sample-llm-call-input tenant-a task-a-id
                                              :external-event-id "shared-ext"))
        evt-b (repo/create-event!
               db
               (support/sample-llm-call-input tenant-b task-b-id
                                              :external-event-id "shared-ext"))]
    (try
      (is (not= (:event/id evt-a) (:event/id evt-b)))
      (finally
        (task-support/delete-task! db task-a-id)
        (task-support/delete-task! db task-b-id)
        (task-support/delete-tenant! db tenant-a)
        (task-support/delete-tenant! db tenant-b)))))

(deftest ^:integration span-from-other-task-rejected-test
  (let [db (integration-db*)
        setup-a (support/insert-tenant-task-span! db)
        setup-b (support/insert-tenant-task-span! db)
        tenant-a (:tenant-id setup-a)
        task-a-id (:task-id setup-a)
        span-b-id (:span/id (:span setup-b))]
    (try
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"another task"
                            (repo/create-event!
                             db
                             (support/sample-llm-call-input tenant-a task-a-id
                                                            :span-id span-b-id))))
      (finally
        (task-support/delete-task! db task-a-id)
        (task-support/delete-task! db (:task-id setup-b))
        (task-support/delete-tenant! db tenant-a)
        (task-support/delete-tenant! db (:tenant-id setup-b))))))

(deftest ^:integration create-event-task-not-found-test
  (let [db (integration-db*)
        tenant-id (task-support/insert-tenant! db)]
    (try
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Task not found"
                            (repo/create-event!
                             db
                             (support/sample-llm-call-input tenant-id (UUID/randomUUID)))))
      (finally
        (task-support/delete-tenant! db tenant-id)))))

(deftest ^:integration delete-task-cascades-events-test
  (let [db (integration-db*)
        {:keys [tenant-id task-id]} (support/insert-tenant-task-span! db)
        _ (repo/create-event!
           db
           (support/sample-llm-call-input tenant-id task-id))
        _ (repo/create-event!
           db
           (support/sample-cache-input tenant-id task-id))]
    (try
      (is (= 2 (support/event-count-for-task db task-id)))
      (task-support/delete-task! db task-id)
      (is (= 0 (support/event-count-for-task db task-id)))
      (finally
        (task-support/delete-tenant! db tenant-id)))))

(deftest ^:integration delete-span-sets-span-id-null-test
  (let [db (integration-db*)
        {:keys [tenant-id task-id span]} (support/insert-tenant-task-span! db)
        span-id (:span/id span)
        created (repo/create-event!
                 db
                 (support/sample-llm-call-input tenant-id task-id :span-id span-id))]
    (try
      (is (= span-id (support/event-span-id db (:event/id created))))
      (span-support/delete-span! db span-id)
      (is (nil? (support/event-span-id db (:event/id created))))
      (is (= 1 (support/event-count-for-task db task-id)))
      (finally
        (task-support/delete-task! db task-id)
        (task-support/delete-tenant! db tenant-id)))))
