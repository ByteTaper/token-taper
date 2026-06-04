;; SPDX-FileCopyrightText: 2026 Haluan Irsad
;; SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Commercial

(ns token-taper.api.routes-test
  (:require
   [clojure.test :refer [deftest is]]
   [jsonista.core :as json]
   [ring.mock.request :as mock]
   [token-taper.api.server :as server]
   [token-taper.health.service :as health-service]
   [token-taper.observability.metrics :as metrics]
   [token-taper.test-support.handler-fixtures :as fixtures]
   [token-taper.test-support.logging :as log-support]))

(def ^:private mapper (json/object-mapper {:decode-key-fn keyword}))

(def ^:private health-system (fixtures/default-health-system (Object.)))

(defn- metrics-component []
  (metrics/create-registry
   {:app (:app health-system)
    :datasource (:datasource health-system)
    :health-config (:health-config health-system)
    :git-sha "unknown"}))

(defn- handler-opts []
  (let [logger (log-support/test-logger)]
    {:system-info (fixtures/system-info-payload (:app health-system) health-system)
     :health-system (assoc health-system :logger logger)
     :metrics (assoc (metrics-component) :logger logger)
     :logger logger}))

(defn- app []
  (server/handler (handler-opts)))

(defn- json-body [response]
  (json/read-value (:body response) mapper))

(deftest health-live-test
  (let [response ((app) (mock/request :get "/health/live"))
        body (json-body response)]
    (is (= 200 (:status response)))
    (is (= "alive" (:status body)))
    (is (string? (get-in response [:headers "x-request-id"])))))

(deftest health-ready-test
  (with-redefs [health-service/ready (constantly {:http-status 200
                                                  :body {:status "ready"
                                                         :service "tokentaper"
                                                         :checks {:config {:status "ok"}}}})]
    (let [response ((app) (mock/request :get "/health/ready"))
          body (json-body response)]
      (is (= 200 (:status response)))
      (is (= "ready" (:status body)))
      (is (= "ok" (get-in body [:checks :config :status]))))))

(deftest not-found-test
  (let [response ((app) (mock/request :get "/unknown"))
        body (json-body response)]
    (is (= 404 (:status response)))
    (is (= "not_found" (get-in body [:error :code])))))

(deftest method-not-allowed-test
  (let [response ((app) (mock/request :post "/health/live"))
        body (json-body response)]
    (is (= 405 (:status response)))
    (is (= "method_not_allowed" (get-in body [:error :code])))))

(deftest json-content-type-test
  (let [response ((app) (mock/request :get "/health/live"))]
    (is (= "application/json; charset=utf-8"
           (get-in response [:headers "content-type"])))))
