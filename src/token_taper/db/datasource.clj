;; SPDX-FileCopyrightText: 2026 Haluan Irsad
;; SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Commercial

(ns token-taper.db.datasource
  (:import
   [com.zaxxer.hikari HikariConfig HikariDataSource]))

(def required-keys
  [:jdbc-url :username :maximum-pool-size :minimum-idle
   :connection-timeout-ms :validation-timeout-ms])

(defn- require-key!
  [config k]
  (when-not (contains? config k)
    (throw (ex-info "Missing required database config key"
                    {:key k
                     :component :token-taper.db/datasource}))))

(defn validate-config!
  [config]
  (doseq [k required-keys]
    (require-key! config k))
  (when-not (pos? (:maximum-pool-size config))
    (throw (ex-info "Database maximum pool size must be positive"
                    {:maximum-pool-size (:maximum-pool-size config)})))
  (when (neg? (:minimum-idle config))
    (throw (ex-info "Database minimum idle must not be negative"
                    {:minimum-idle (:minimum-idle config)})))
  (when (> (:minimum-idle config) (:maximum-pool-size config))
    (throw (ex-info "Database minimum idle must not exceed maximum pool size"
                    {:minimum-idle (:minimum-idle config)
                     :maximum-pool-size (:maximum-pool-size config)})))
  (doseq [[k] (filter (fn [[_ v]] (and v (number? v) (not (pos? v))))
                      (select-keys config [:connection-timeout-ms
                                           :validation-timeout-ms
                                           :idle-timeout-ms
                                           :max-lifetime-ms]))]
    (throw (ex-info "Database timeout must be positive"
                    {:key k :value (get config k)})))
  config)

(defn make-hikari-config
  [config]
  (validate-config! config)
  (doto (HikariConfig.)
    (.setInitializationFailTimeout -1)
    (.setJdbcUrl (:jdbc-url config))
    (.setUsername (:username config))
    (.setPassword (:password config ""))
    (.setPoolName (:pool-name config "token-taper-db"))
    (.setMaximumPoolSize (:maximum-pool-size config))
    (.setMinimumIdle (:minimum-idle config))
    (.setConnectionTimeout (:connection-timeout-ms config))
    (.setValidationTimeout (:validation-timeout-ms config))
    (.setIdleTimeout (:idle-timeout-ms config 600000))
    (.setMaxLifetime (:max-lifetime-ms config 1800000))
    (.setAutoCommit (boolean (:auto-commit config true)))))

(defn make-datasource
  [config]
  (HikariDataSource. (make-hikari-config config)))

(defn datasource?
  [x]
  (instance? HikariDataSource x))

(defn close-datasource!
  [datasource]
  (when (and datasource (datasource? datasource) (not (.isClosed datasource)))
    (.close datasource))
  nil)
