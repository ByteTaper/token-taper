;; SPDX-FileCopyrightText: 2026 Haluan Irsad
;; SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Commercial

(ns token-taper.main
  (:gen-class)
  (:require
   [token-taper.db.datasource :as datasource]
   [token-taper.db.migration :as migration]
   [token-taper.observability.logging :as logging]
   [token-taper.system.components]
   [token-taper.system.config :as config]
   [token-taper.system.integrant :as system])
  (:import
   [java.lang.management ManagementFactory]))

(defn- pid
  []
  (let [name (ManagementFactory/getRuntimeMXBean)]
    (Long/parseLong (subs (.getName name) 0 (.indexOf (.getName name) "@")))))

(defn- logger-from-config
  [cfg]
  (logging/create-context
   {:app (:token-taper/app cfg)
    :logging-config (or (:token-taper.logging/config cfg) {})}))

(defn- startup-fields
  [cfg]
  {:http_port (get-in cfg [:token-taper/http :port])
   :java_version (System/getProperty "java.version")
   :clojure_version (clojure-version)
   :pid (pid)})

(defn start-api! []
  (let [done (promise)
        stop-started (atom nil)
        cfg (config/load-config)
        logger (logger-from-config cfg)]
    (logging/info! logger :service_starting (startup-fields cfg))
    (try
      (let [sys (system/start-system! cfg)]
        (.addShutdownHook
         (Runtime/getRuntime)
         (Thread.
          #(let [started (or @stop-started (System/currentTimeMillis))]
             (reset! stop-started started)
             (logging/info! logger :service_stopping {})
             (system/stop-system! sys)
             (logging/info! logger :service_stopped
                            {:duration_ms (- (System/currentTimeMillis) started)})
             (deliver done :stopped))))
        (logging/info! logger :service_started
                       (assoc (startup-fields cfg)
                              :http_port (get-in sys [:token-taper/http-server :port])))
        @done)
      (catch Throwable t
        (logging/fatal! logger :service_start_failed
                        (merge (startup-fields cfg)
                               (logging/build-error-fields logger t)))
        (throw t)))))

(defn run-migrations! []
  (let [cfg (config/load-config)
        logger (logger-from-config cfg)
        mig-cfg (:token-taper.db/migration cfg)
        ds (datasource/make-datasource (:token-taper.db/datasource cfg))]
    (try
      (migration/run-migrations!
       {:datasource ds
        :migration-dir (:migration-dir mig-cfg)
        :logger logger})
      (catch Exception e
        (logging/error! logger :migration_failed
                        (logging/build-error-fields logger e))
        (System/exit 1))
      (finally
        (datasource/close-datasource! ds)))))

(defn usage []
  (str "Usage: token-taper <mode>\n\n"
       "Modes:\n"
       "  api      Start API service\n"
       "  migrate  Run database migrations (Migratus)\n"))

(defn -main [& args]
  (let [mode (or (first args) "api")]
    (case mode
      "api" (start-api!)
      "migrate" (run-migrations!)
      (do
        (binding [*out* *err*]
          (println (usage))
          (println "Unknown mode:" mode))
        (System/exit 2)))))
