;; SPDX-FileCopyrightText: 2026 Haluan Irsad
;; SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Commercial

(ns token-taper.main
  (:gen-class)
  (:require
   [token-taper.system.components]
   [token-taper.system.config :as config]
   [token-taper.system.integrant :as system]))

(defn start-api! []
  (let [done (promise)
        cfg (config/load-config)
        sys (system/start-system! cfg)]
    (.addShutdownHook
     (Runtime/getRuntime)
     (Thread.
      #(do
         (system/stop-system! sys)
         (deliver done :stopped))))
    (println "TokenTaper system started")
    (println
     (pr-str {:service (get-in sys [:token-taper/app :service-name])
              :version (get-in sys [:token-taper/app :service-version])
              :environment (get-in sys [:token-taper/app :environment])
              :http-port (get-in sys [:token-taper/http-server :port])}))
    @done))

(defn run-migrations! []
  (let [cfg (config/load-config)]
    (println "TokenTaper migration mode placeholder started")
    (println "No migrations are defined; config environment:"
             (get-in cfg [:token-taper/app :environment]))))

(defn usage []
  (str "Usage: token-taper <mode>\n\n"
       "Modes:\n"
       "  api      Start API service\n"
       "  migrate  Run migration placeholder\n"))

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
