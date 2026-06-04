;; SPDX-FileCopyrightText: 2026 Haluan Irsad
;; SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Commercial

(ns token-taper.api.middleware
  (:require
   [clojure.string :as str]
   [token-taper.api.error :as error]
   [token-taper.api.response :as response]
   [token-taper.observability.logging :as logging])
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

(defn wrap-request-logging
  [handler logger]
  (fn [request]
    (let [started (System/currentTimeMillis)]
      (try
        (let [response (handler request)
              duration-ms (- (System/currentTimeMillis) started)
              status (or (:status response) 500)]
          (when logger
            (logging/emit!
             logger
             (logging/level-for-status status)
             :http_request_completed
             (logging/build-request-log-event logger request response duration-ms)))
          response)
        (catch Throwable t
          (when logger
            (let [duration-ms (- (System/currentTimeMillis) started)]
              (logging/error!
               logger
               :http_request_failed
               (merge (logging/build-request-log-event
                       logger
                       request
                       {:status 500}
                       duration-ms)
                      (logging/build-error-fields logger t)))))
          (throw t))))))

(defn wrap-exception
  ([handler] (wrap-exception handler nil))
  ([handler logger]
   (fn [request]
     (try
       (handler request)
       (catch Throwable t
         (error/log-unhandled-exception! logger request t)
         (response/internal-error "Internal server error" (:request-id request)))))))

(defn wrap-basic-headers
  [handler]
  (fn [request]
    (let [response (handler request)]
      (update response :headers merge {"X-Content-Type-Options" "nosniff"
                                       "Cache-Control" "no-store"}))))
