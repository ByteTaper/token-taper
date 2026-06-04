;; SPDX-FileCopyrightText: 2026 Haluan Irsad
;; SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Commercial

(ns token-taper.api.health-routes-test
  (:require
   [clojure.test :refer [deftest is]]
   [jsonista.core :as json]
   [ring.mock.request :as mock]
   [token-taper.api.server :as server]
   [token-taper.db.datasource :as datasource]
   [token-taper.db.migration :as migration]
   [token-taper.db.test-support :as support]
   [token-taper.health.service :as health-service]
   [token-taper.system.config :as config]
   [token-taper.test-support.logging :as log-support]))

(def ^:private mapper (json/object-mapper {:decode-key-fn keyword}))

(def ^:private system-info
  {:service "token-taper"
   :version "0.1.0-SNAPSHOT"
   :environment "test"})

(defn- health-system
  ([]
   (health-system nil))
  ([datasource]
   {:app {:service-name "token-taper"
          :service-version "0.1.0-SNAPSHOT"
          :environment "test"
          :status :started}
    :config {:status :loaded}
    :datasource datasource
    :health-config {:database-timeout-ms 1000}}))

(defn- app [health-sys]
  (server/handler {:system-info system-info
                   :health-system health-sys}))

(defn- json-body [response]
  (json/read-value (:body response) mapper))

(deftest health-live-flat-json-test
  (let [response ((app (health-system)) (mock/request :get "/health/live"))
        body (json-body response)]
    (is (= 200 (:status response)))
    (is (= "alive" (:status body)))
    (is (= "tokentaper" (:service body)))
    (is (nil? (:data body)))))

(deftest health-ready-503-when-database-unavailable-test
  (let [cfg (assoc support/valid-unit-config
                   :jdbc-url "jdbc:postgresql://127.0.0.1:1/nonexistent"
                   :connection-timeout-ms 500
                   :validation-timeout-ms 500)
        ds (datasource/make-datasource cfg)]
    (try
      (let [response ((app (health-system ds)) (mock/request :get "/health/ready"))
            body (json-body response)]
        (is (= 503 (:status response)))
        (is (= "not_ready" (:status body)))
        (is (= "error" (get-in body [:checks :database :status])))
        (is (nil? (:data body))))
      (finally
        (datasource/close-datasource! ds)))))

(deftest health-ready-200-when-checks-pass-test
  (with-redefs [health-service/ready (constantly {:http-status 200
                                                  :body {:status "ready"
                                                         :service "tokentaper"
                                                         :checks {:config {:status "ok"}}}})]
    (let [response ((app (health-system)) (mock/request :get "/health/ready"))
          body (json-body response)]
      (is (= 200 (:status response)))
      (is (= "ready" (:status body))))))

(deftest ^:integration health-ready-after-migration-test
  (when (support/integration-db-available?)
    (let [ds-cfg (support/load-test-datasource-config)
          app-cfg (get (config/load-config "resources/config.test.edn") :token-taper/app)
          mig-cfg (get (config/load-config "resources/config.test.edn") :token-taper.db/migration)
          ds (datasource/make-datasource ds-cfg)]
      (try
        (migration/migrate! {:datasource ds
                             :migration-dir (:migration-dir mig-cfg)
                             :logger (log-support/test-logger)})
        (let [response ((app (health-system ds)) (mock/request :get "/health/ready"))
              body (json-body response)]
          (is (= 200 (:status response)))
          (is (= "ready" (:status body)))
          (is (= "ok" (get-in body [:checks :migrations :status]))))
        (finally
          (datasource/close-datasource! ds))))))
