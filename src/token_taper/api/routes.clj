;; SPDX-FileCopyrightText: 2026 Haluan Irsad
;; SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Commercial

(ns token-taper.api.routes
  (:require
   [reitit.ring :as ring]
   [token-taper.api.response :as response]))

(defn- request-id [request]
  (:request-id request))

(defn live-handler
  [request]
  (response/ok {:status "ok"} (request-id request)))

(defn ready-handler
  [request]
  (response/ok {:status "ready"
                :checks {:http "ok"}}
               (request-id request)))

(defn system-info-handler
  [system-info]
  (fn [request]
    (response/ok system-info (request-id request))))

(defn router
  [{:keys [system-info]}]
  (ring/router
   [["/health/live" {:get live-handler}]
    ["/health/ready" {:get ready-handler}]
    ["/v1/system/info" {:get (system-info-handler system-info)}]]))
