;; SPDX-FileCopyrightText: 2026 Haluan Irsad
;; SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Commercial

(ns token-taper.db.health
  (:require
   [next.jdbc :as jdbc]
   [token-taper.health.checks :as checks]))

(defn- default-timeout-ms
  [_opts]
  1000)

(defn- with-timeout
  [timeout-ms f]
  (let [result (deref (future (f)) timeout-ms ::timeout)]
    (when (= ::timeout result)
      (throw (ex-info "Database health check timed out" {:timeout-ms timeout-ms})))
    result))

(defn database-ready?
  ([datasource]
   (database-ready? datasource {}))
  ([datasource {:keys [timeout-ms] :or {timeout-ms (default-timeout-ms nil)}}]
   (if (nil? datasource)
     (checks/error :database-unreachable "Database datasource is not available")
     (try
       (with-timeout timeout-ms
         #(do
            (jdbc/execute-one! datasource ["SELECT 1 AS ok"])
            (checks/ok)))
       (catch Exception _
         (checks/error :database-unreachable "Database readiness check failed"))))))

(defn migrations-ready?
  ([datasource]
   (migrations-ready? datasource {}))
  ([datasource {:keys [timeout-ms] :or {timeout-ms (default-timeout-ms nil)}}]
   (if (nil? datasource)
     (checks/unknown :database-unreachable)
     (try
       (let [row (with-timeout timeout-ms
                   #(jdbc/execute-one!
                     datasource
                     ["SELECT EXISTS (
                         SELECT 1
                         FROM information_schema.tables
                         WHERE table_schema = 'public'
                           AND table_name = 'schema_migrations'
                       ) AS exists"]))]
         (if (:exists row)
           (checks/ok)
           (checks/error :migrations-unavailable "schema_migrations table is missing")))
       (catch Exception _
         (checks/unknown :database-unreachable))))))

(defn database-ready-value
  ([datasource]
   (database-ready-value datasource {}))
  ([datasource opts]
   (if (checks/ok? (database-ready? datasource opts))
     1
     0)))

(defn check-ready
  [datasource]
  (let [check (database-ready? datasource)]
    (if (checks/ok? check)
      {:status :ok
       :component :database
       :details {:query "SELECT 1"}}
      {:status :error
       :component :database
       :error {:reason (:reason check)
               :message (:message check)}})))
