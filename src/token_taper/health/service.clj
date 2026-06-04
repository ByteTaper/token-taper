;; SPDX-FileCopyrightText: 2026 Haluan Irsad
;; SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Commercial

(ns token-taper.health.service
  (:require
   [token-taper.db.health :as db-health]
   [token-taper.health.checks :as checks]))

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
  [app check-name check-result duration-ms]
  (when-not (checks/ok? check-result)
    (println
     (pr-str
      {:event "readiness_check_failed"
       :service (service-name app)
       :version (:service-version app)
       :env (:environment app)
       :check (name check-name)
       :status (name (:status check-result))
       :reason (some-> (:reason check-result) name)
       :duration_ms duration-ms}))))

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
  [app check-name f health-system]
  (let [started (System/currentTimeMillis)
        result (f health-system)
        duration-ms (- (System/currentTimeMillis) started)]
    (when-not (checks/ok? result)
      (log-readiness-failure! app check-name result duration-ms))
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
        app (:app health-system)
        include-details? (:include-details? (:health-config health-system))
        checks {:config (run-check app :config config-check health-system)
                :system (run-check app :system system-check health-system)
                :database (run-check app :database database-check health-system)
                :migrations (run-check app :migrations migrations-check health-system)}
        all-ok? (every? checks/ok? (vals checks))
        body {:status (if all-ok? "ready" "not_ready")
              :service (service-name app)
              :checks (checks->json checks include-details?)}]
    {:http-status (if all-ok? 200 503)
     :body body}))
