;; SPDX-FileCopyrightText: 2026 Haluan Irsad
;; SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Commercial

(ns token-taper.system.config
  (:require
   [aero.core :as aero]
   [clojure.java.io :as io]
   [integrant.core :as ig]))

(defmethod aero.core/reader 'ig/ref
  ([_ _tag value]
   (ig/ref value)))

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

(defn- classpath-resource-name [path]
  (if (.startsWith path "resources/")
    (subs path (count "resources/"))
    path))

(defn- load-integrant-config [path]
  (let [resource-name (classpath-resource-name path)
        resource-or-file (or (io/resource resource-name)
                             (io/resource path)
                             (io/file path))]
    (when-not resource-or-file
      (throw (ex-info "Config not found" {:path path})))
    (aero/read-config resource-or-file)))

(defn load-config
  ([]
   (load-config (config-path)))
  ([path]
   (-> path
       load-integrant-config
       validate-config!)))
