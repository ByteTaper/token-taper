;; SPDX-FileCopyrightText: 2026 Haluan Irsad
;; SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Commercial

(ns token-taper.api.response
  (:require
   [jsonista.core :as json]))

(def ^:private content-type "application/json; charset=utf-8")

(def ^:private mapper (json/object-mapper {:encode-key-fn name}))

(defn- encode-body [body]
  (json/write-value-as-string body mapper))

(defn json-response
  [status body]
  {:status status
   :headers {"content-type" content-type}
   :body (encode-body body)})

(defn- envelope
  [data error request-id]
  (cond-> {:data data :error error}
    request-id (assoc :request_id request-id)))

(defn ok
  ([data] (ok data nil))
  ([data request-id]
   (json-response 200 (envelope data nil request-id))))

(defn created
  ([data] (created data nil))
  ([data request-id]
   (json-response 201 (envelope data nil request-id))))

(defn accepted
  ([data] (accepted data nil))
  ([data request-id]
   (json-response 202 (envelope data nil request-id))))

(defn no-content
  ([] (no-content nil))
  ([_request-id]
   {:status 204 :headers {"content-type" content-type} :body ""}))

(defn bad-request
  ([message] (bad-request message nil))
  ([message request-id]
   (json-response 400 (envelope nil {:code "bad_request" :message message} request-id))))

(defn not-found
  ([{:keys [message request-id] :or {message "Route not found"}}]
   (json-response 404 (envelope nil {:code "not_found" :message message} request-id))))

(defn method-not-allowed
  ([{:keys [message request-id] :or {message "Method not allowed"}}]
   (json-response 405 (envelope nil {:code "method_not_allowed" :message message} request-id))))

(defn internal-error
  ([message] (internal-error message nil))
  ([message request-id]
   (json-response 500 (envelope nil {:code "internal_error" :message message} request-id))))

(defn diagnostic-ok
  "Flat JSON body for safe public diagnostics (e.g. GET /v1/system/info)."
  [body]
  (json-response 200 body))

(defn health-ok
  [body]
  (json-response 200 body))

(defn health-not-ready
  [body]
  (json-response 503 body))

(defn health-internal-error
  ([message] (health-internal-error message nil))
  ([message _request-id]
   (json-response 500 {:status "error" :message message})))
