;; SPDX-FileCopyrightText: 2026 Haluan Irsad
;; SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Commercial

(ns token-taper.api.server-test
  (:require
   [clojure.test :refer [deftest is]]
   [ring.mock.request :as mock]
   [token-taper.api.server :as server]
   [token-taper.system.components]
   [token-taper.system.config :as config]
   [token-taper.observability.metrics :as metrics]
   [token-taper.system.integrant :as system]
   [token-taper.test-support.handler-fixtures :as fixtures]
   [token-taper.test-support.logging :as log-support]))

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

(deftest handler-without-jetty-test
  (let [response ((server/handler (handler-opts))
                  (mock/request :get "/health/live"))]
    (is (= 200 (:status response)))))

(deftest start-and-stop-server-test
  (let [http-config {:host "127.0.0.1" :port 0 :join? false :stop-timeout-ms 1000}
        component (server/start-server! http-config (handler-opts))]
    (try
      (is (pos? (:port component)))
      (is (some? (:server component)))
      (finally
        (server/stop-server! component)))))

(deftest integrant-http-lifecycle-test
  (let [cfg (config/load-config "resources/config.test.edn")
        sys (system/start-system! cfg)]
    (try
      (is (= :started (get-in sys [:token-taper/http-server :status])))
      (is (pos? (get-in sys [:token-taper/http-server :port])))
      (finally
        (system/stop-system! sys)))))
