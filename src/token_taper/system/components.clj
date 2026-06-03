;; SPDX-FileCopyrightText: 2026 Haluan Irsad
;; SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Commercial

(ns token-taper.system.components
  (:require
   [integrant.core :as ig]
   [token-taper.api.server :as http-server]
   [token-taper.api.system-info :as system-info]))

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

(defmethod ig/init-key :token-taper/http-server
  [_ {:keys [config app]}]
  (let [info (system-info/build app)]
    (-> (http-server/start-server! config {:system-info info})
        (assoc :status :started))))

(defmethod ig/halt-key! :token-taper/http-server
  [_ component]
  (http-server/stop-server! component))

(defmethod ig/init-key :token-taper/config
  [_ config]
  (assoc config :status :loaded))

(defmethod ig/halt-key! :token-taper/config
  [_ _]
  nil)
