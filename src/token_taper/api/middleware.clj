;; SPDX-FileCopyrightText: 2026 Haluan Irsad
;; SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Commercial

(ns token-taper.api.middleware
  (:require
   [clojure.string :as str]
   [token-taper.api.response :as response])
  (:import
   (java.util UUID)))

(defn- generate-request-id []
  (str "req_" (UUID/randomUUID)))

(defn- request-id-from-headers
  [request]
  (let [headers (:headers request)]
    (or (get headers "x-request-id")
        (get headers "X-Request-Id")
        (some (fn [[k v]]
                (when (= "x-request-id" (str/lower-case (name k)))
                  v))
              headers))))

(defn wrap-request-id
  [handler]
  (fn [request]
    (let [request-id (or (request-id-from-headers request)
                         (generate-request-id))
          request' (assoc request :request-id request-id)
          response (handler request')]
      (assoc-in response [:headers "x-request-id"] request-id))))

(defn wrap-exception
  [handler]
  (fn [request]
    (try
      (handler request)
      (catch Throwable _
        (response/internal-error "Internal server error" (:request-id request))))))

(defn wrap-basic-headers
  [handler]
  (fn [request]
    (let [response (handler request)]
      (update response :headers merge {"X-Content-Type-Options" "nosniff"
                                       "Cache-Control" "no-store"}))))
