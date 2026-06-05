;; SPDX-FileCopyrightText: 2026 Haluan Irsad
;; SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Commercial

(ns token-taper.task.api-schema
  (:require
   [malli.core :as m]
   [malli.error :as me]
   [token-taper.task.errors :as errors])
  (:import
   [java.time Instant]
   [java.util UUID]))

(def StartTaskRequest
  [:map
   [:tenant_id uuid?]
   [:external_task_id {:optional true} [:maybe :string]]
   [:workflow {:optional true} [:maybe :string]]
   [:task_type {:optional true} [:maybe :string]]
   [:started_at {:optional true} [:maybe :any]]
   [:metadata {:optional true} [:map-of :keyword :any]]])

(def ^:private uuid-string?
  [:re #"^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"])

(def ^:private rfc3339-string?
  ;; ISO-8601 instant strings emitted in JSON (spec domain equivalent: inst?).
  [:re #"^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,9})?(?:Z|[+-]\d{2}:\d{2})$"])

(def StartTaskResponse
  "Malli schema for POST /v1/tasks/start 201 response body (JSON wire shape).
   Spec lists uuid? / inst? for domain values; API responses use UUID and RFC3339 strings."
  [:map
   [:task_id uuid-string?]
   [:tenant_id uuid-string?]
   [:external_task_id {:optional true} [:maybe :string]]
   [:workflow {:optional true} [:maybe :string]]
   [:task_type {:optional true} [:maybe :string]]
   [:status [:= "started"]]
   [:started_at rfc3339-string?]
   [:finished_at [:maybe rfc3339-string?]]
   [:metadata [:map-of :keyword :any]]])

;; ACG-0204 suggested name
(def start-task-response-schema StartTaskResponse)

(defn- explain->details
  [explain]
  (let [humanized (me/humanize explain)]
    (if (map? humanized)
      (mapv (fn [[field messages]]
              {:field (name field)
               :reason (if (coll? messages) (first messages) (str messages))})
            humanized)
      [{:field "body" :reason (str humanized)}])))

(defn- ->uuid
  [value]
  (try
    (cond
      (instance? UUID value) value
      (string? value) (UUID/fromString value)
      :else value)
    (catch IllegalArgumentException _
      value)))

(defn- validation-detail
  [field reason]
  {:field field :reason reason})

(defn- ->instant
  [value]
  (try
    (cond
      (nil? value) nil
      (instance? Instant value) value
      (string? value) (Instant/parse value)
      :else
      (throw (errors/validation-error
              "Invalid task start request."
              {:details [(validation-detail "started_at" "invalid")]})))
    (catch java.time.format.DateTimeParseException _
      (throw (errors/validation-error
              "Invalid task start request."
              {:details [(validation-detail "started_at" "invalid")]})))))

(defn normalize-start-request
  [input]
  (cond-> input
    (:tenant_id input) (update :tenant_id ->uuid)
    (contains? input :started_at) (update :started_at ->instant)
    true (update :metadata #(or % {}))))

(defn validate-start-request!
  [input]
  (let [normalized (-> input
                       (update :metadata #(or % {}))
                       normalize-start-request)]
    (if (m/validate StartTaskRequest normalized)
      normalized
      (throw (errors/validation-error
              "Invalid task start request."
              {:details (explain->details (m/explain StartTaskRequest normalized))})))))

(defn- instant->string
  [value]
  (when value (str value)))

(defn- uuid->string
  [value]
  (when value (str value)))

(defn task->start-response
  [task]
  {:task_id (uuid->string (:task/id task))
   :tenant_id (uuid->string (:tenant/id task))
   :external_task_id (:task/external-id task)
   :workflow (:task/workflow task)
   :task_type (:task/type task)
   :status "started"
   :started_at (instant->string (:task/started-at task))
   :finished_at (instant->string (:task/finished-at task))
   :metadata (or (:task/metadata task) {})})

(defn validate-start-response!
  [response]
  (if (m/validate StartTaskResponse response)
    response
    (throw (ex-info "Invalid task start response."
                    {:error/kind :internal
                     :error/message "Invalid task start response."
                     :error/details (explain->details
                                     (m/explain StartTaskResponse response))}))))

(defn start-task-response
  "Build and validate the 201 response body for POST /v1/tasks/start."
  [task]
  (-> task task->start-response validate-start-response!))

(defn validation-details->api
  [details]
  (cond
    (vector? details) details
    (map? details) (mapv (fn [[k v]] {:field (name k) :reason (str v)}) details)
    (string? details) [{:field "body" :reason details}]
    :else [{:field "body" :reason "invalid"}]))

(defn conflict-details->api
  [details]
  (if (and (map? details) (:external_task_id details))
    [{:field "external_task_id" :reason "duplicate"}]
    [{:field "external_task_id" :reason "duplicate"}]))
