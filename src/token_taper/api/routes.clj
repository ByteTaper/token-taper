;; SPDX-FileCopyrightText: 2026 Haluan Irsad
;; SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Commercial

(ns token-taper.api.routes
  (:require
   [reitit.ring :as ring]
   [token-taper.api.response :as response]
   [token-taper.health.service :as health]
   [token-taper.observability.metrics :as metrics]))

(defn system-info-handler
  [system-info]
  (fn [request]
    (response/ok system-info (:request-id request))))

(defn live-handler
  [health-system]
  (fn [_request]
    (let [{:keys [body]} (health/live health-system)]
      (response/health-ok body))))

(defn ready-handler
  [health-system]
  (fn [_request]
    (let [{:keys [http-status body]} (health/ready health-system)]
      (if (= 200 http-status)
        (response/health-ok body)
        (response/health-not-ready body)))))

(defn metrics-handler
  [metrics-component]
  (fn [_request]
    (metrics/scrape metrics-component)))

(defn router
  [{:keys [system-info health-system metrics]}]
  (ring/router
   [["/health/live" {:get (live-handler health-system)}]
    ["/health/ready" {:get (ready-handler health-system)}]
    ["/metrics" {:get (metrics-handler metrics)}]
    ["/v1/system/info" {:get (system-info-handler system-info)}]]))
