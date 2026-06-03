;; SPDX-FileCopyrightText: 2026 Haluan Irsad
;; SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Commercial

(ns token-taper.api.response-test
  (:require
   [clojure.test :refer [deftest is]]
   [jsonista.core :as json]
   [token-taper.api.response :as response]))

(def ^:private mapper (json/object-mapper {:decode-key-fn keyword}))

(defn- parse-body [response]
  (json/read-value (:body response) mapper))

(deftest ok-response-test
  (let [resp (response/ok {:status "ok"} "req_test")
        body (parse-body resp)]
    (is (= 200 (:status resp)))
    (is (= "application/json; charset=utf-8" (get-in resp [:headers "content-type"])))
    (is (= {:status "ok"} (:data body)))
    (is (nil? (:error body)))
    (is (= "req_test" (:request_id body)))))

(deftest not-found-response-test
  (let [resp (response/not-found {:message "missing" :request-id "req_404"})
        body (parse-body resp)]
    (is (= 404 (:status resp)))
    (is (= "not_found" (get-in body [:error :code])))
    (is (nil? (:data body)))))
