;; SPDX-FileCopyrightText: 2026 Haluan Irsad
;; SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Commercial

(ns token-taper.system.config
  (:require
   [aero.core :as aero]
   [clojure.java.io :as io]))

(def default-config-path "resources/config.edn")

(defn config-path []
  (or (System/getenv "TOKEN_TAPER_CONFIG")
      default-config-path))

(defn required-key?
  [m k]
  (contains? m k))

(defn validate-config!
  [config]
  (doseq [k [:token-taper/app :token-taper/http]]
    (when-not (required-key? config k)
      (throw (ex-info "Missing required config key"
                      {:missing-key k}))))
  config)

(defn load-config
  ([]
   (load-config (config-path)))
  ([path]
   (let [resource-or-file (or (io/resource path)
                              (io/file path))]
     (-> resource-or-file
         aero/read-config
         validate-config!))))
