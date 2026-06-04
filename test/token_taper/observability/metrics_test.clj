;; SPDX-FileCopyrightText: 2026 Haluan Irsad
;; SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Commercial

(ns token-taper.observability.metrics-test
  (:require
   [clojure.test :refer [deftest is]]
   [token-taper.db.health :as db-health]
   [token-taper.observability.metrics :as metrics]))

(defn- test-component
  []
  (metrics/create-registry
   {:app {:service-name "token-taper"
          :service-version "0.1.0-SNAPSHOT"
          :environment "test"}
    :datasource (Object.)
    :health-config {:database-timeout-ms 100}
    :git-sha "test-sha"}))

(deftest registry-initializes-test
  (let [component (test-component)
        text (metrics/scrape-text component)]
    (is (some? (:registry component)))
    (is (pos? (:started-at component)))
    (is (re-find #"tokentaper_uptime_seconds" text))
    (is (re-find #"tokentaper_build_info" text))
    (is (re-find #"tokentaper_http_requests_total" text))
    (is (re-find #"tokentaper_database_ready" text))))

(deftest record-http-request-increments-counter-test
  (let [component (test-component)]
    (metrics/record-http-request! component
                                  {:method "GET"
                                   :route "/health/live"
                                   :status "200"
                                   :duration-s 0.01})
    (let [text (metrics/scrape-text component)]
      (is (re-find #"tokentaper_http_requests_total\{method=\"GET\",route=\"/health/live\",status=\"200\""
                   text))
      (is (re-find #"tokentaper_http_request_duration_seconds" text)))))

(deftest record-http-request-increments-error-counter-for-5xx-test
  (let [component (test-component)]
    (metrics/record-http-request! component
                                  {:method "GET"
                                   :route "/health/live"
                                   :status "500"
                                   :duration-s 0.02})
    (let [text (metrics/scrape-text component)]
      (is (re-find #"tokentaper_http_errors_total\{method=\"GET\",route=\"/health/live\",status=\"500\""
                   text)))))

(deftest update-database-ready-sets-gauge-test
  (with-redefs [db-health/database-ready-value (constantly 1)]
    (let [component (test-component)
          text (metrics/scrape-text component)]
      (is (re-find #"tokentaper_database_ready 1" text))))
  (with-redefs [db-health/database-ready-value (constantly 0)]
    (let [component (test-component)
          text (metrics/scrape-text component)]
      (is (re-find #"tokentaper_database_ready 0" text)))))

(deftest scrape-returns-metrics-response-test
  (let [response (metrics/scrape (test-component))]
    (is (= 200 (:status response)))
    (is (= metrics/metrics-content-type (get-in response [:headers "content-type"])))
    (is (string? (:body response)))))
