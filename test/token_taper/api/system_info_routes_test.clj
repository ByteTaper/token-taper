;; SPDX-FileCopyrightText: 2026 Haluan Irsad
;; SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Commercial

(ns token-taper.api.system-info-routes-test
  (:require
   [clojure.test :refer [deftest is]]
   [jsonista.core :as json]
   [ring.mock.request :as mock]
   [token-taper.api.server :as server]
   [token-taper.test-support.handler-fixtures :as fixtures]
   [token-taper.test-support.logging :as log-support]
   [token-taper.test-support.system-info :as info-support]))

(def ^:private mapper (json/object-mapper {:decode-key-fn keyword}))

(def ^:private health-system (fixtures/default-health-system (Object.)))

(defn- app []
  (let [logger (log-support/test-logger)]
    (server/handler {:system-info (fixtures/system-info-payload (:app health-system) health-system)
                     :health-system (assoc health-system :logger logger)
                     :logger logger})))

(defn- json-body [response]
  (json/read-value (:body response) mapper))

(deftest get-system-info-returns-200-and-json-test
  (let [response ((app) (mock/request :get "/v1/system/info"))]
    (is (= 200 (:status response)))
    (is (= "application/json; charset=utf-8"
           (get-in response [:headers "content-type"])))))

(deftest get-system-info-flat-body-test
  (let [body (json-body ((app) (mock/request :get "/v1/system/info")))]
    (is (= "tokentaper" (:service body)))
    (is (= "TokenTaper" (:name body)))
    (is (string? (:version body)))
    (is (= "test" (:environment body)))
    (is (nil? (:data body)))
    (is (string? (get-in body [:runtime :jvm])))
    (is (string? (get-in body [:runtime :java_version])))
    (is (string? (get-in body [:runtime :clojure_version])))
    (is (string? (get-in body [:build :git_sha])))))

(deftest get-system-info-excludes-forbidden-fields-test
  (let [body (json-body ((app) (mock/request :get "/v1/system/info")))]
    (is (not (info-support/forbidden-fields-present? body)))))
