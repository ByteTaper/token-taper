;; SPDX-FileCopyrightText: 2026 Haluan Irsad
;; SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Commercial

(ns token-taper.api.metrics-routes-test
  (:require
   [clojure.test :refer [deftest is]]
   [ring.mock.request :as mock]
   [token-taper.api.server :as server]
   [token-taper.observability.metrics :as metrics]))

(def ^:private system-info
  {:service "token-taper"
   :version "0.1.0-SNAPSHOT"
   :environment "test"})

(def ^:private health-system
  {:app {:service-name "token-taper"
         :service-version "0.1.0-SNAPSHOT"
         :environment "test"
         :status :started}
   :config {:status :loaded}
   :datasource (Object.)
   :health-config {:database-timeout-ms 100}})

(defn- metrics-component []
  (metrics/create-registry
   {:app (:app health-system)
    :datasource (:datasource health-system)
    :health-config (:health-config health-system)
    :git-sha "unknown"}))

(defn- app []
  (server/handler {:system-info system-info
                   :health-system health-system
                   :metrics (metrics-component)}))

(deftest metrics-endpoint-returns-200-and-text-plain-test
  (let [response ((app) (mock/request :get "/metrics"))]
    (is (= 200 (:status response)))
    (is (re-find #"text/plain" (get-in response [:headers "content-type"])))
    (is (re-find #"version=0.0.4" (get-in response [:headers "content-type"])))))

(deftest metrics-body-contains-required-metrics-test
  (let [body (:body ((app) (mock/request :get "/metrics")))]
    (is (re-find #"tokentaper_uptime_seconds" body))
    (is (re-find #"tokentaper_http_requests_total" body))
    (is (re-find #"tokentaper_database_ready" body))))

(deftest health-live-increments-http-request-metric-test
  (let [app-fn (app)]
    (app-fn (mock/request :get "/health/live"))
    (let [body (:body (app-fn (mock/request :get "/metrics")))]
      (is (re-find #"tokentaper_http_requests_total\{method=\"GET\",route=\"/health/live\",status=\"200\""
                   body)))))
