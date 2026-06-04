;; SPDX-FileCopyrightText: 2026 Haluan Irsad
;; SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Commercial

(ns token-taper.system.components
  (:require
   [integrant.core :as ig]
   [token-taper.api.server :as http-server]
   [token-taper.api.system-info :as system-info]
   [token-taper.db.datasource :as datasource]
   [token-taper.observability.metrics :as observability-metrics]))

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

(defmethod ig/init-key :token-taper/http-server
  [_ {:keys [config app datasource loaded-config health-config metrics]}]
  (let [info (system-info/build app)
        health-system {:app app
                       :config loaded-config
                       :datasource datasource
                       :health-config (or health-config {})}]
    (-> (http-server/start-server! config {:system-info info
                                           :health-system health-system
                                           :metrics metrics})
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
