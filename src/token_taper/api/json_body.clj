;; SPDX-FileCopyrightText: 2026 Haluan Irsad
;; SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Commercial

(ns token-taper.api.json-body
  (:require
   [clojure.string :as str]
   [jsonista.core :as json]
   [token-taper.api.response :as response])
  (:import
   (java.io ByteArrayInputStream)))

(def ^:private decode-mapper
  (json/object-mapper {:decode-key-fn keyword}))

(defn- json-content-type?
  [request]
  (when-let [ct (get-in request [:headers "content-type"])]
    (str/starts-with? (str/lower-case ct) "application/json")))

(defn- read-body-bytes
  [request]
  (cond
    (bytes? (:body request)) (:body request)
    (string? (:body request)) (.getBytes ^String (:body request) "UTF-8")
    (instance? java.io.InputStream (:body request))
    (.readAllBytes ^java.io.InputStream (:body request))
    :else nil))

(defn wrap-json-body
  [handler]
  (fn [request]
    (if (and (#{:post :put :patch} (:request-method request))
             (json-content-type? request))
      (let [body-bytes (read-body-bytes request)]
        (if (or (nil? body-bytes) (zero? (alength body-bytes)))
          (handler (assoc request :json-body {}))
          (try
            (let [parsed (json/read-value
                          (ByteArrayInputStream. body-bytes)
                          decode-mapper)]
              (handler (assoc request :json-body parsed)))
            (catch Exception _
              (response/validation-error-response
               "Invalid JSON request body."
               [{:field "body" :reason "invalid_json"}])))))
      (handler request))))
