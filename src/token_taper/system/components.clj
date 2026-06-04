;; SPDX-FileCopyrightText: 2026 Haluan Irsad
;; SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Commercial

(ns token-taper.system.components
  (:require
   [integrant.core :as ig]
   [token-taper.api.server :as http-server]
   [token-taper.db.datasource :as datasource]
   [token-taper.observability.logging :as logging]
   [token-taper.observability.metrics :as observability-metrics]
   [token-taper.system.build :as build]
   [token-taper.system.info :as system-info]))

(defmethod ig/init-key :token-taper/app
  [_ config]
  (assoc config :status :started))

(defmethod ig/halt-key! :token-taper/app
  [_ _]
  nil)

(defmethod ig/init-key :token-taper/http
  [_ config]
  config)

(defmethod ig/halt-key! :token-taper/http
  [_ _]
  nil)

(defmethod ig/init-key :token-taper.health/checks
  [_ config]
  config)

(defmethod ig/halt-key! :token-taper.health/checks
  [_ _]
  nil)

(defmethod ig/init-key :token-taper.logging/config
  [_ config]
  config)

(defmethod ig/halt-key! :token-taper.logging/config
  [_ _]
  nil)

(defmethod ig/init-key :token-taper.logging/context
  [_ opts]
  (logging/create-context opts))

(defmethod ig/halt-key! :token-taper.logging/context
  [_ _]
  nil)

(defmethod ig/init-key :token-taper.system/info
  [_ config]
  config)

(defmethod ig/halt-key! :token-taper.system/info
  [_ _]
  nil)

(defmethod ig/init-key :token-taper.observability/metrics
  [_ {:keys [app datasource health-config git-sha]}]
  (observability-metrics/create-registry
   {:app app
    :datasource datasource
    :health-config health-config
    :git-sha git-sha}))

(defmethod ig/halt-key! :token-taper.observability/metrics
  [_ _]
  nil)

(defn- resolve-system-info-payload
  [app system-meta logger]
  (let [{:keys [build-info failed? source throwable]}
        (build/load-build-info!)]
    (when (and failed? logger)
      (logging/warn! logger :build_info_load_failed
                     (merge {:source source
                             :service "tokentaper"
                             :version (:service-version app "unknown")
                             :env (:environment app "unknown")}
                            (when throwable
                              (logging/build-error-fields logger throwable)))))
    (system-info/build-for-app app (build/merge-overrides app build-info)
                               :system-meta system-meta)))

(defmethod ig/init-key :token-taper/http-server
  [_ {:keys [config app datasource loaded-config health-config metrics logger system-info-config]}]
  (let [info (resolve-system-info-payload app system-info-config logger)
        health-system {:app app
                       :config loaded-config
                       :datasource datasource
                       :health-config (or health-config {})
                       :logger logger}
        metrics' (assoc metrics :logger logger)]
    (-> (http-server/start-server! config {:system-info info
                                           :health-system health-system
                                           :metrics metrics'
                                           :logger logger})
        (assoc :status :started))))

(defmethod ig/halt-key! :token-taper/http-server
  [_ component]
  (http-server/stop-server! component))

(defmethod ig/init-key :token-taper.db/migration
  [_ config]
  config)

(defmethod ig/halt-key! :token-taper.db/migration
  [_ _]
  nil)

(defmethod ig/init-key :token-taper.db/datasource
  [_ config]
  (datasource/make-datasource config))

(defmethod ig/halt-key! :token-taper.db/datasource
  [_ ds]
  (datasource/close-datasource! ds))

(defmethod ig/init-key :token-taper/config
  [_ config]
  (assoc config :status :loaded))

(defmethod ig/halt-key! :token-taper/config
  [_ _]
  nil)
