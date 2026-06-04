;; SPDX-FileCopyrightText: 2026 Haluan Irsad
;; SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Commercial

(ns token-taper.db.migration
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [migratus.core :as migratus]
   [token-taper.observability.logging :as logging])
  (:import
   [java.io File]))

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

(defn- migratus-migration-dir
  "Migratus requires a path relative to user.dir; resolve absolute dirs for Docker."
  [migration-dir]
  (let [dir-file (io/file migration-dir)]
    (if (.isAbsolute dir-file)
      (.toString (.relativize (.toPath (io/file (System/getProperty "user.dir")))
                              (.toPath dir-file)))
      migration-dir)))

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
  [{:keys [datasource migration-dir logger] :as opts}]
  (let [resolved-dir (resolve-migration-dir! migration-dir)
        migratus-dir (migratus-migration-dir migration-dir)
        started (System/currentTimeMillis)]
    (logging/info! logger :migration_started {:migration_dir resolved-dir})
    (try
      (verify-connection! datasource)
      (let [result (migratus/migrate (migratus-config datasource migratus-dir))]
        (logging/info! logger :migration_completed
                       {:migration_dir resolved-dir
                        :duration_ms (- (System/currentTimeMillis) started)
                        :applied result})
        result)
      (catch Exception e
        (logging/error! logger :migration_failed
                        (merge {:migration_dir resolved-dir
                                :duration_ms (- (System/currentTimeMillis) started)}
                               (logging/build-error-fields logger e)))
        (throw e)))))

(defn rollback!
  [{:keys [datasource migration-dir logger] :as opts}]
  (let [resolved-dir (resolve-migration-dir! migration-dir)
        migratus-dir (migratus-migration-dir migration-dir)
        started (System/currentTimeMillis)]
    (logging/info! logger :migration_started
                   {:migration_dir resolved-dir :operation "rollback"})
    (try
      (verify-connection! datasource)
      (let [result (migratus/rollback (migratus-config datasource migratus-dir))]
        (logging/info! logger :migration_completed
                       {:migration_dir resolved-dir
                        :duration_ms (- (System/currentTimeMillis) started)
                        :operation "rollback"
                        :rolled-back result})
        result)
      (catch Exception e
        (logging/error! logger :migration_failed
                        (merge {:migration_dir resolved-dir
                                :duration_ms (- (System/currentTimeMillis) started)
                                :operation "rollback"}
                               (logging/build-error-fields logger e)))
        (throw e)))))

(defn run-migrations!
  [opts]
  (migrate! opts))
