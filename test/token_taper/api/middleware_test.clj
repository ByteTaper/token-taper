;; SPDX-FileCopyrightText: 2026 Haluan Irsad
;; SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Commercial

(ns token-taper.api.middleware-test
  (:require
   [clojure.test :refer [deftest is]]
   [jsonista.core :as json]
   [ring.mock.request :as mock]
   [token-taper.api.middleware :as middleware]
   [token-taper.observability.http-metrics :as http-metrics]
   [token-taper.observability.metrics :as metrics]
   [token-taper.test-support.logging :as log-support]))

(def ^:private mapper (json/object-mapper {:decode-key-fn keyword}))

(defn- app []
  (let [logger (log-support/test-logger)]
    (-> (fn [request]
          (if (= "/boom" (:uri request))
            (throw (ex-info "boom" {}))
            {:status 200 :body "ok" :headers {}}))
        middleware/wrap-basic-headers
        (middleware/wrap-exception logger)
        middleware/wrap-request-id)))

(deftest preserves-request-id-test
  (let [request (assoc (mock/request :get "/")
                       :headers {"x-request-id" "req_existing"})
        response ((app) request)]
    (is (= "req_existing" (get-in response [:headers "x-request-id"])))))

(deftest generates-request-id-test
  (let [response ((app) (mock/request :get "/"))]
    (is (string? (get-in response [:headers "x-request-id"])))
    (is (re-find #"^req_" (get-in response [:headers "x-request-id"])))))

(deftest exception-becomes-structured-500-test
  (let [request (assoc (mock/request :get "/boom")
                       :headers {"x-request-id" "req_err"})
        response ((app) request)
        body (json/read-value (:body response) mapper)]
    (is (= 500 (:status response)))
    (is (= "internal_error" (get-in body [:error :code])))
    (when-let [body-str (:body response)]
      (is (nil? (re-find #"Exception" body-str)))
      (is (nil? (re-find #"stack" body-str))))))

(defn- metrics-app []
  (let [logger (log-support/test-logger)
        metrics-component (metrics/create-registry
                           {:app {:service-version "0.1.0-SNAPSHOT" :environment "test"}
                            :datasource (Object.)
                            :health-config {:database-timeout-ms 100}
                            :git-sha "unknown"})]
    (-> (fn [request]
          (cond
            (= "/boom" (:uri request))
            (throw (ex-info "boom" {}))

            (= "/error" (:uri request))
            {:status 500 :body "error" :headers {}}

            :else
            {:status 200 :body "ok" :headers {}}))
        middleware/wrap-basic-headers
        (http-metrics/wrap-http-metrics metrics-component)
        (middleware/wrap-request-logging logger)
        (middleware/wrap-exception logger)
        middleware/wrap-request-id)))

(deftest http-metrics-increments-request-counter-test
  (let [metrics-component (metrics/create-registry
                           {:app {:service-version "0.1.0-SNAPSHOT" :environment "test"}
                            :datasource (Object.)
                            :health-config {:database-timeout-ms 100}
                            :git-sha "unknown"})
        handler (-> (fn [_] {:status 200 :body "ok"})
                    (http-metrics/wrap-http-metrics metrics-component))]
    (handler (mock/request :get "/health/live"))
    (let [text (metrics/scrape-text metrics-component)]
      (is (re-find #"tokentaper_http_requests_total\{method=\"GET\",route=\"/health/live\",status=\"200\""
                   text)))))

(deftest http-metrics-increments-error-counter-for-5xx-test
  (let [metrics-component (metrics/create-registry
                           {:app {:service-version "0.1.0-SNAPSHOT" :environment "test"}
                            :datasource (Object.)
                            :health-config {:database-timeout-ms 100}
                            :git-sha "unknown"})
        handler (-> (fn [_] {:status 500 :body "error"})
                    (http-metrics/wrap-http-metrics metrics-component))]
    (handler (mock/request :get "/error"))
    (let [text (metrics/scrape-text metrics-component)]
      (is (re-find #"tokentaper_http_errors_total\{method=\"GET\",route=\"unknown\",status=\"500\""
                   text)))))

(deftest http-metrics-preserves-exception-behavior-test
  (let [response ((metrics-app) (mock/request :get "/boom"))
        body (json/read-value (:body response) mapper)]
    (is (= 500 (:status response)))
    (is (= "internal_error" (get-in body [:error :code])))))
