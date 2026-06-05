;; SPDX-FileCopyrightText: 2026 Haluan Irsad
;; SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Commercial

(ns token-taper.db.migration-test
  (:require
   [clojure.java.io :as io]
   [clojure.test :refer [deftest is testing]]
   [token-taper.db.datasource :as datasource]
   [token-taper.db.jdbc :as jdbc]
   [token-taper.db.migration :as migration]
   [token-taper.db.test-support :as support]
   [token-taper.test-support.logging :as log-support]
   [token-taper.main :as main]
   [token-taper.system.config :as config]))

(def migrations-dir "migrations")

(def expected-migration-files
  #{"001-create-tenant-table.up.sql"
    "001-create-tenant-table.down.sql"
    "002-create-api-key-table.up.sql"
    "002-create-api-key-table.down.sql"
    "003-create-audit-log-table.up.sql"
    "003-create-audit-log-table.down.sql"
    "004-create-ai-task-table.up.sql"
    "004-create-ai-task-table.down.sql"
    "005-create-ai-span-table.up.sql"
    "005-create-ai-span-table.down.sql"
    "006-create-ai-event-table.up.sql"
    "006-create-ai-event-table.down.sql"})

(defn- migration-files []
  (->> (io/file migrations-dir)
       .listFiles
       (map #(.getName ^java.io.File %))
       set))

(defn- up-migration-files []
  (->> (migration-files)
       (filter #(.endsWith % ".up.sql"))
       set))

(defn table-exists?
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

(deftest migratus-config-shape-test
  (let [ds (datasource/make-datasource support/valid-unit-config)
        cfg (migration/migratus-config ds "migrations")]
    (try
      (is (= :database (:store cfg)))
      (is (= "migrations" (:migration-dir cfg)))
      (is (= ds (get-in cfg [:db :datasource])))
      (finally
        (datasource/close-datasource! ds)))))

(deftest migration-files-exist-test
  (is (= expected-migration-files (migration-files))))

(deftest every-up-migration-has-down-test
  (doseq [up (up-migration-files)]
    (let [down (.replace up ".up.sql" ".down.sql")]
      (is (contains? (migration-files) down)
          (str "missing down migration for " up)))))

(deftest resolve-migration-dir-fails-for-missing-dir-test
  (try
    (migration/resolve-migration-dir! "nonexistent-migrations-dir-xyz")
    (is false "expected ex-info")
    (catch clojure.lang.ExceptionInfo e
      (is (re-find #"Migration directory does not exist" (.getMessage e))))))

(deftest resolve-migration-dir-resolves-relative-path-test
  (is (re-find #"migrations$"
               (migration/resolve-migration-dir! migrations-dir))))

(deftest run-migrations-fn-exists-test
  (is (fn? main/run-migrations!))
  (is (fn? migration/migrate!)))

(deftest load-config-includes-migration-test
  (let [cfg (config/load-config "resources/config.test.edn")]
    (is (= "migrations" (get-in cfg [:token-taper.db/migration :migration-dir])))))

(deftest ^:integration migrate-creates-schema-test
  (when (support/integration-db-available?)
    (let [ds-cfg (support/load-test-datasource-config)
          mig-cfg (get (config/load-config "resources/config.test.edn")
                       :token-taper.db/migration)
          logger (log-support/test-logger)
          ds (datasource/make-datasource ds-cfg)]
      (try
        (migration/migrate!
         {:datasource ds
          :migration-dir (:migration-dir mig-cfg)
          :logger logger})
        (is (table-exists? ds "tenant"))
        (is (table-exists? ds "api_key"))
        (is (table-exists? ds "audit_log"))
        (is (table-exists? ds "ai_task"))
        (is (table-exists? ds "ai_span"))
        (is (table-exists? ds "ai_event"))
        (is (table-exists? ds "schema_migrations"))
        (finally
          (datasource/close-datasource! ds))))))

(deftest ^:integration migrate-is-idempotent-test
  (when (support/integration-db-available?)
    (let [ds-cfg (support/load-test-datasource-config)
          mig-cfg (get (config/load-config "resources/config.test.edn")
                       :token-taper.db/migration)
          logger (log-support/test-logger)
          ds (datasource/make-datasource ds-cfg)]
      (try
        (migration/migrate!
         {:datasource ds
          :migration-dir (:migration-dir mig-cfg)
          :logger logger})
        (migration/migrate!
         {:datasource ds
          :migration-dir (:migration-dir mig-cfg)
          :logger logger})
        (is (table-exists? ds "tenant"))
        (finally
          (datasource/close-datasource! ds))))))

(deftest ^:integration rollback-removes-tables-test
  (when (support/integration-db-available?)
    (let [ds-cfg (support/load-test-datasource-config)
          mig-cfg (get (config/load-config "resources/config.test.edn")
                       :token-taper.db/migration)
          logger (log-support/test-logger)
          opts {:datasource nil
                :migration-dir (:migration-dir mig-cfg)
                :logger logger}
          ds (datasource/make-datasource ds-cfg)]
      (try
        (migration/migrate! (assoc opts :datasource ds))
        (migration/rollback! (assoc opts :datasource ds))
        (is (not (table-exists? ds "ai_event")))
        (is (table-exists? ds "ai_span"))
        (migration/rollback! (assoc opts :datasource ds))
        (is (not (table-exists? ds "ai_span")))
        (is (table-exists? ds "ai_task"))
        (migration/rollback! (assoc opts :datasource ds))
        (is (not (table-exists? ds "ai_task")))
        (is (table-exists? ds "audit_log"))
        (migration/rollback! (assoc opts :datasource ds))
        (is (not (table-exists? ds "audit_log")))
        (is (table-exists? ds "api_key"))
        (migration/rollback! (assoc opts :datasource ds))
        (is (not (table-exists? ds "api_key")))
        (is (table-exists? ds "tenant"))
        (migration/rollback! (assoc opts :datasource ds))
        (is (not (table-exists? ds "tenant")))
        (migration/migrate! (assoc opts :datasource ds))
        (is (table-exists? ds "audit_log"))
        (finally
          (datasource/close-datasource! ds))))))
