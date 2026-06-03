;; SPDX-FileCopyrightText: 2026 Haluan Irsad
;; SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Commercial

(ns token-taper.api.middleware-test
  (:require
   [clojure.test :refer [deftest is]]
   [jsonista.core :as json]
   [ring.mock.request :as mock]
   [token-taper.api.middleware :as middleware]))

(def ^:private mapper (json/object-mapper {:decode-key-fn keyword}))

(defn- app []
  (-> (fn [request]
        (if (= "/boom" (:uri request))
          (throw (ex-info "boom" {}))
          {:status 200 :body "ok" :headers {}}))
      middleware/wrap-basic-headers
      middleware/wrap-exception
      middleware/wrap-request-id))

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
