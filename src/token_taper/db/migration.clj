;; SPDX-FileCopyrightText: 2026 Haluan Irsad
;; SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Commercial

(ns token-taper.db.migration
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [migratus.core :as migratus])
  (:import
   [java.io File]))

(defn- redact-jdbc-url
  [jdbc-url]
  (when jdbc-url
    (str/replace jdbc-url #"(//[^:/?#]+:)[^@]+@" "$1***@")))

(defn sanitize-for-log
  [m]
  (cond-> m
    (:jdbc-url m) (update :jdbc-url redact-jdbc-url)))

(defn log-event!
  [event app fields]
  (let [base {:event event
              :service (:service-name app "token-taper")
              :version (:service-version app)
              :env (:environment app)}]
    (println (pr-str (sanitize-for-log (merge base fields))))))

(defn migratus-config
  [datasource migration-dir]
  {:store :database
   :migration-dir migration-dir
   :db {:datasource datasource}})

(defn- migration-dir-has-sql?
  [dir]
  (boolean
   (some #(.endsWith (.getName ^File %) ".sql")
         (file-seq dir))))

(defn resolve-migration-dir!
  [migration-dir]
  (let [dir-file (if (.isAbsolute (io/file migration-dir))
                   (io/file migration-dir)
                   (io/file (System/getProperty "user.dir") migration-dir))]
    (when-not (.exists dir-file)
      (throw (ex-info "Migration directory does not exist"
                      {:migration-dir migration-dir
                       :resolved (.getAbsolutePath dir-file)})))
    (when-not (.isDirectory dir-file)
      (throw (ex-info "Migration path is not a directory"
                      {:migration-dir migration-dir
                       :resolved (.getAbsolutePath dir-file)})))
    (when-not (migration-dir-has-sql? dir-file)
      (throw (ex-info "Migration directory contains no SQL files"
                      {:migration-dir migration-dir
                       :resolved (.getAbsolutePath dir-file)})))
    (.getAbsolutePath dir-file)))

(defn verify-connection!
  [datasource]
  (with-open [conn (.getConnection datasource)]
    (with-open [stmt (.createStatement conn)]
      (.execute stmt "SELECT 1"))))

(defn migrate!
  [{:keys [datasource migration-dir app]}]
  (let [resolved-dir (resolve-migration-dir! migration-dir)
        started (System/currentTimeMillis)]
    (log-event! "migration_started" app {:migration_dir resolved-dir})
    (try
      (verify-connection! datasource)
      (let [result (migratus/migrate (migratus-config datasource resolved-dir))]
        (log-event! "migration_completed" app
                    {:migration_dir resolved-dir
                     :duration_ms (- (System/currentTimeMillis) started)
                     :applied result})
        result)
      (catch Exception e
        (log-event! "migration_failed" app
                    {:migration_dir resolved-dir
                     :duration_ms (- (System/currentTimeMillis) started)
                     :error_class (.. e getClass getName)
                     :error_message (.getMessage e)})
        (throw e)))))

(defn rollback!
  [{:keys [datasource migration-dir app]}]
  (let [resolved-dir (resolve-migration-dir! migration-dir)
        started (System/currentTimeMillis)]
    (log-event! "migration_started" app
                {:migration_dir resolved-dir :operation "rollback"})
    (try
      (verify-connection! datasource)
      (let [result (migratus/rollback (migratus-config datasource resolved-dir))]
        (log-event! "migration_completed" app
                    {:migration_dir resolved-dir
                     :duration_ms (- (System/currentTimeMillis) started)
                     :operation "rollback"
                     :rolled-back result})
        result)
      (catch Exception e
        (log-event! "migration_failed" app
                    {:migration_dir resolved-dir
                     :duration_ms (- (System/currentTimeMillis) started)
                     :operation "rollback"
                     :error_class (.. e getClass getName)
                     :error_message (.getMessage e)})
        (throw e)))))

(defn run-migrations!
  [opts]
  (migrate! opts))
