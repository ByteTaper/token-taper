;; SPDX-FileCopyrightText: 2026 Haluan Irsad
;; SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Commercial

(ns token-taper.observability.metrics
  (:require
   [clojure.string :as str]
   [iapetos.core :as i]
   [iapetos.export :as export]
   [token-taper.db.health :as db-health]
   [token-taper.observability.runtime-metrics :as runtime-metrics]))

(def metrics-content-type
  "text/plain; version=0.0.4; charset=utf-8")

(def histogram-buckets
  [0.005 0.01 0.025 0.05 0.1 0.25 0.5 1.0 2.5 5.0])

(def http-labels
  [:method :route :status])

(defn- normalize-export-text
  [text]
  (str/replace text #"default_tokentaper_" "tokentaper_"))

(defn- register-collectors!
  [registry]
  (i/register registry
              (i/gauge :tokentaper_uptime_seconds
                       {:description "Service uptime in seconds."})
              (i/gauge :tokentaper_build_info
                       {:labels [:service :version :env :git_sha]
                        :description "Build information."})
              (i/counter :tokentaper_http_requests_total
                         {:labels http-labels
                          :description "Total HTTP requests."})
              (i/histogram :tokentaper_http_request_duration_seconds
                           {:labels http-labels
                            :buckets histogram-buckets
                            :description "HTTP request duration in seconds."})
              (i/counter :tokentaper_http_errors_total
                         {:labels http-labels
                          :description "Total HTTP 5xx responses."})
              (i/gauge :tokentaper_database_ready
                       {:description "Database readiness state."})
              (i/gauge :tokentaper_jvm_memory_used_bytes
                       {:description "JVM memory used in bytes."})
              (i/gauge :tokentaper_jvm_memory_committed_bytes
                       {:description "JVM memory committed in bytes."})
              (i/gauge :tokentaper_jvm_threads_live
                       {:description "Live JVM thread count."})))

(defn- build-info-labels
  [{:keys [app git-sha]}]
  {:service "tokentaper"
   :version (:service-version app "unknown")
   :env (:environment app "unknown")
   :git_sha (or git-sha "unknown")})

(defn create-registry
  [{:keys [app datasource health-config git-sha]}]
  (let [started-at (System/currentTimeMillis)
        registry (register-collectors! (i/collector-registry))
        component {:registry registry
                   :started-at started-at
                   :app app
                   :datasource datasource
                   :health-config (or health-config {})
                   :git-sha git-sha}]
    (i/set registry :tokentaper_build_info (build-info-labels component) 1)
    component))

(defn uptime-seconds
  [{:keys [started-at]}]
  (/ (- (System/currentTimeMillis) started-at) 1000.0))

(defn record-http-request!
  [{:keys [registry]} {:keys [method route status duration-s]}]
  (let [labels {:method method :route route :status status}
        status-code (try (Integer/parseInt status) (catch Exception _ 500))]
    (i/inc registry :tokentaper_http_requests_total labels)
    (i/observe registry :tokentaper_http_request_duration_seconds labels duration-s)
    (when (>= status-code 500)
      (i/inc registry :tokentaper_http_errors_total labels))))

(defn update-database-ready!
  [{:keys [registry datasource health-config]}]
  (let [timeout-ms (:database-timeout-ms health-config 1000)
        value (db-health/database-ready-value datasource {:timeout-ms timeout-ms})]
    (i/set registry :tokentaper_database_ready value)))

(defn update-runtime-metrics!
  [{:keys [registry]}]
  (let [used (runtime-metrics/jvm-memory-used-bytes)
        committed (runtime-metrics/jvm-memory-committed-bytes)]
    (i/set registry :tokentaper_jvm_memory_used_bytes (:total used))
    (i/set registry :tokentaper_jvm_memory_committed_bytes (:total committed))
    (i/set registry :tokentaper_jvm_threads_live (runtime-metrics/jvm-threads-live))))

(defn scrape-text
  [component]
  (update-runtime-metrics! component)
  (i/set (:registry component) :tokentaper_uptime_seconds (uptime-seconds component))
  (update-database-ready! component)
  (normalize-export-text (export/text-format (:registry component))))

(defn log-scrape-failure!
  [component exception]
  (println
   (pr-str
    {:event "metrics_exposition_failed"
     :service "tokentaper"
     :version (get-in component [:app :service-version])
     :env (get-in component [:app :environment])
     :error_class (.getName (class exception))
     :error_message (.getMessage exception)})))

(defn metrics-response
  [body]
  {:status 200
   :headers {"content-type" metrics-content-type}
   :body body})

(defn metrics-error-response
  []
  {:status 500
   :headers {"content-type" "text/plain; charset=utf-8"}
   :body "metrics collection failed\n"})

(defn scrape
  [component]
  (try
    (metrics-response (scrape-text component))
    (catch Exception e
      (log-scrape-failure! component e)
      (metrics-error-response))))
