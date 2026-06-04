;; SPDX-FileCopyrightText: 2026 Haluan Irsad
;; SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Commercial

(ns token-taper.observability.http-metrics
  (:require
   [clojure.string :as str]
   [token-taper.observability.metrics :as metrics]
   [token-taper.observability.route :as route]))

(defn wrap-http-metrics
  [handler metrics-component]
  (fn [request]
    (let [start (System/nanoTime)
          response (handler request)
          duration-s (/ (- (System/nanoTime) start) 1e9)
          method (-> request :request-method name str/upper-case)
          route (route/route-label request)
          status (str (or (:status response) 500))]
      (metrics/record-http-request! metrics-component
                                    {:method method
                                     :route route
                                     :status status
                                     :duration-s duration-s})
      response)))
