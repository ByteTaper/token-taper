// SPDX-FileCopyrightText: 2026 Haluan Irsad
// SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Commercial

(ns token-taper.main
  (:gen-class))

(def service-name "token-taper")
(def version "0.1.0-SNAPSHOT")

(defn system-info []
  {:service service-name
   :version version})

(defn start-api! []
  (println "TokenTaper API mode placeholder started")
  (println (pr-str (system-info))))

(defn run-migrations! []
  (println "TokenTaper migration mode placeholder started")
  (println "No migrations are defined in ACG-0101"))

(defn usage []
  (str "Usage: token-taper <mode>\n\n"
       "Modes:\n"
       "  api      Start API service placeholder\n"
       "  migrate  Run migration placeholder\n"))

(defn -main [& args]
  (let [mode (or (first args) "api")]
    (case mode
      "api"     (start-api!)
      "migrate" (run-migrations!)
      (do
        (binding [*out* *err*]
          (println (usage))
          (println "Unknown mode:" mode))
        (System/exit 2)))))
