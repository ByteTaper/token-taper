;; SPDX-FileCopyrightText: 2026 Haluan Irsad
;; SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Commercial

(ns token-taper.api.logging-middleware-test
  (:require
   [clojure.test :refer [deftest is]]
   [jsonista.core :as json]
   [ring.mock.request :as mock]
   [token-taper.api.middleware :as middleware]
   [token-taper.test-support.logging :as log-support]))

(def ^:private mapper (json/object-mapper {:decode-key-fn keyword}))

(defn- stack-app
  [inner]
  (let [logger (log-support/test-logger)]
    (-> inner
        middleware/wrap-basic-headers
        (middleware/wrap-request-logging logger)
        (middleware/wrap-exception logger)
        middleware/wrap-request-id)))

(defn- find-event
  [entries event-name]
  (some #(when (= (name event-name) (:event %)) %) entries))

(deftest preserves-request-id-test
  (let [{:keys [result entries]}
        (log-support/capture-logs!
         (fn [_logger]
           ((stack-app (fn [_] {:status 200 :body "ok"}))
            (assoc (mock/request :get "/health/live")
                   :headers {"x-request-id" "req_existing"}))))]
    (is (= "req_existing" (get-in result [:headers "x-request-id"])))
    (let [completed (find-event entries :http_request_completed)]
      (is (= "req_existing" (:request_id completed)))
      (is (= "info" (:level completed))))))

(deftest generates-request-id-test
  (let [{:keys [result entries]}
        (log-support/capture-logs!
         (fn [_logger]
           ((stack-app (fn [_] {:status 200 :body "ok"}))
            (mock/request :get "/health/live"))))]
    (is (re-find #"^req_" (get-in result [:headers "x-request-id"])))
    (is (some? (find-event entries :http_request_completed)))))

(deftest http-request-completed-includes-duration-test
  (let [{:keys [entries]}
        (log-support/capture-logs!
         (fn [_logger]
           ((stack-app (fn [_] {:status 200 :body "ok"}))
            (mock/request :get "/health/live"))))]
    (let [completed (find-event entries :http_request_completed)]
      (is (= "GET" (:method completed)))
      (is (= "/health/live" (:path completed)))
      (is (= 200 (:status completed)))
      (is (number? (:duration_ms completed))))))

(deftest http-500-response-logs-error-level-test
  (let [{:keys [entries]}
        (log-support/capture-logs!
         (fn [_logger]
           ((stack-app (fn [_] {:status 500 :body "error"}))
            (mock/request :get "/error"))))]
    (let [completed (find-event entries :http_request_completed)]
      (is (= "error" (:level completed)))
      (is (= 500 (:status completed))))))

(deftest thrown-exception-logs-unhandled-and-returns-500-test
  (let [{:keys [result entries]}
        (log-support/capture-logs!
         (fn [_logger]
           ((stack-app (fn [_] (throw (ex-info "boom" {}))))
            (assoc (mock/request :get "/boom")
                   :headers {"x-request-id" "req_err"}))))]
    (is (= 500 (:status result)))
    (is (some? (find-event entries :unhandled_exception)))
    (let [failed (find-event entries :http_request_failed)]
      (is (= "req_err" (:request_id failed)))
      (is (= "error" (:level failed)))
      (is (= 500 (:status failed)))
      (is (= "boom" (:error_message failed)))
      (is (number? (:duration_ms failed))))
    (is (nil? (find-event entries :http_request_completed)))
    (let [body (json/read-value (:body result) mapper)]
      (is (= "internal_error" (get-in body [:error :code]))))))
