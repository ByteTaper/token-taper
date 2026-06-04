;; SPDX-FileCopyrightText: 2026 Haluan Irsad
;; SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Commercial

(ns token-taper.health.service
  (:require
   [token-taper.db.health :as db-health]
   [token-taper.health.checks :as checks]
   [token-taper.observability.logging :as logging]))

(def default-health-config
  {:database-timeout-ms 1000
   :include-details? true})

(defn- health-config
  [health-system]
  (merge default-health-config (:health-config health-system {})))

(def health-service-name
  "tokentaper")

(defn service-name
  [_app]
  health-service-name)

(defn log-readiness-failure!
  [logger check-name check-result duration-ms]
  (when (and logger (not (checks/ok? check-result)))
    (logging/warn! logger :database_readiness_failed
                   {:check (name check-name)
                    :status (name (:status check-result))
                    :reason (some-> (:reason check-result) name)
                    :duration_ms duration-ms})))

(defn- config-check
  [{:keys [config]}]
  (if (= :loaded (:status config))
    (checks/ok)
    (checks/error :config-not-loaded "Configuration is not loaded")))

(defn- system-check
  [{:keys [app datasource]}]
  (if (and (= :started (:status app)) (some? datasource))
    (checks/ok)
    (checks/error :system-not-started "System is not fully initialized")))

(defn- database-check
  [{:keys [datasource health-config]}]
  (db-health/database-ready? datasource {:timeout-ms (:database-timeout-ms health-config)}))

(defn- migrations-check
  [{:keys [datasource health-config] :as health-system}]
  (let [db (database-check health-system)]
    (cond
      (checks/error? db) (checks/unknown :database-unreachable)
      (checks/unknown? db) db
      :else (db-health/migrations-ready? datasource
                                         {:timeout-ms (:database-timeout-ms health-config)}))))

(defn- run-check
  [health-system check-name f]
  (let [logger (:logger health-system)
        started (System/currentTimeMillis)
        result (f health-system)
        duration-ms (- (System/currentTimeMillis) started)]
    (when-not (checks/ok? result)
      (log-readiness-failure! logger check-name result duration-ms))
    result))

(defn- checks->json
  [checks include-details?]
  (into {}
        (map (fn [[k v]]
               [k (checks/->json-check v include-details?)])
             checks)))

(defn live
  [health-system]
  {:http-status 200
   :body {:status "alive"
          :service (service-name (:app health-system))}})

(defn ready
  [health-system]
  (let [health-system (assoc health-system :health-config (health-config health-system))
        include-details? (:include-details? (:health-config health-system))
        checks {:config (run-check health-system :config config-check)
                :system (run-check health-system :system system-check)
                :database (run-check health-system :database database-check)
                :migrations (run-check health-system :migrations migrations-check)}
        all-ok? (every? checks/ok? (vals checks))
        body {:status (if all-ok? "ready" "not_ready")
              :service (service-name (:app health-system))
              :checks (checks->json checks include-details?)}]
    {:http-status (if all-ok? 200 503)
     :body body}))
