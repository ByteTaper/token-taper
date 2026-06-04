;; SPDX-FileCopyrightText: 2026 Haluan Irsad
;; SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Commercial

(ns token-taper.system.config
  (:require
   [aero.core :as aero]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [integrant.core :as ig]
   [token-taper.observability.logging :as logging]))

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

(defn- require-non-blank!
  [config path]
  (let [value (get-in config path)]
    (when (or (nil? value)
              (and (string? value) (str/blank? value)))
      (throw (ex-info "Missing or blank required config value"
                      {:path path
                       :value value})))))

(defn validate-config!
  [config]
  (doseq [k [:token-taper/app
             :token-taper/http
             :token-taper.db/datasource
             :token-taper.db/migration]]
    (when-not (required-key? config k)
      (throw (ex-info "Missing required config key"
                      {:missing-key k}))))
  (require-non-blank! config [:token-taper.db/migration :migration-dir])
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
   (try
     (-> path
         load-integrant-config
         validate-config!)
     (catch Throwable t
       (let [ctx (logging/bootstrap-context)]
         (logging/error! ctx :config_load_failed
                         (merge (logging/build-error-fields ctx t)
                                {:config_path path})))
       (throw t)))))
