;; SPDX-FileCopyrightText: 2026 Haluan Irsad
;; SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Commercial

(ns token-taper.task.api-schema
  (:require
   [malli.core :as m]
   [malli.error :as me]
   [token-taper.task.errors :as errors]
   [token-taper.time.instant :as time-instant])
  (:import
   [java.util UUID]))

(def StartTaskRequest
  [:map
   [:tenant_id uuid?]
   [:external_task_id {:optional true} [:maybe :string]]
   [:workflow {:optional true} [:maybe :string]]
   [:task_type {:optional true} [:maybe :string]]
   [:started_at {:optional true} [:maybe inst?]]
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

(defn normalize-start-request
  [input]
  (cond-> input
    (:tenant_id input) (update :tenant_id ->uuid)
    (contains? input :started_at)
    (update :started_at #(time-instant/coerce-instant-field!
                          "started_at"
                          %
                          "Invalid task start request."))
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

(def FinishTaskRequest
  [:map
   [:status [:enum :finished :failed :cancelled]]
   [:finished_at {:optional true} [:maybe inst?]]
   [:metadata {:optional true} [:map-of :keyword :any]]])

(def FinishTaskResponse
  "Malli schema for POST /v1/tasks/{task_id}/finish 200 response body (JSON wire shape)."
  [:map
   [:task_id uuid-string?]
   [:tenant_id uuid-string?]
   [:external_task_id {:optional true} [:maybe :string]]
   [:workflow {:optional true} [:maybe :string]]
   [:task_type {:optional true} [:maybe :string]]
   [:status [:enum "finished" "failed" "cancelled"]]
   [:started_at rfc3339-string?]
   [:finished_at rfc3339-string?]
   [:metadata [:map-of :keyword :any]]])

(def finish-task-response-schema FinishTaskResponse)

(defn parse-task-id!
  [task-id]
  (try
    (cond
      (instance? UUID task-id) task-id
      (string? task-id) (UUID/fromString task-id)
      :else
      (throw (errors/validation-error
              "Invalid task finish request."
              {:details [(validation-detail "task_id" "invalid")]})))
    (catch IllegalArgumentException _
      (throw (errors/validation-error
              "Invalid task finish request."
              {:details [(validation-detail "task_id" "invalid")]})))))

(defn- ->finish-status
  [value]
  (cond
    (keyword? value) value
    (string? value) (keyword value)
    :else value))

(defn normalize-finish-request
  [input]
  (cond-> input
    (:status input) (update :status ->finish-status)
    (contains? input :finished_at)
    (update :finished_at #(time-instant/coerce-instant-field!
                           "finished_at"
                           %
                           "Invalid task finish request."))
    true (update :metadata #(or % {}))))

(defn validate-finish-request!
  [input]
  (let [normalized (-> input
                       (update :metadata #(or % {}))
                       normalize-finish-request)]
    (if (m/validate FinishTaskRequest normalized)
      normalized
      (throw (errors/validation-error
              "Invalid task finish request."
              {:details (explain->details (m/explain FinishTaskRequest normalized))})))))

(defn task->finish-response
  [task]
  {:task_id (uuid->string (:task/id task))
   :tenant_id (uuid->string (:tenant/id task))
   :external_task_id (:task/external-id task)
   :workflow (:task/workflow task)
   :task_type (:task/type task)
   :status (name (:task/status task))
   :started_at (instant->string (:task/started-at task))
   :finished_at (instant->string (:task/finished-at task))
   :metadata (or (:task/metadata task) {})})

(defn validate-finish-response!
  [response]
  (if (m/validate FinishTaskResponse response)
    response
    (throw (ex-info "Invalid task finish response."
                    {:error/kind :internal
                     :error/message "Invalid task finish response."
                     :error/details (explain->details
                                     (m/explain FinishTaskResponse response))}))))

(defn finish-task-response
  "Build and validate the 200 response body for POST /v1/tasks/{task_id}/finish."
  [task]
  (-> task task->finish-response validate-finish-response!))

(defn finish-conflict-details->api
  [_details]
  [{:field "status" :reason "already_terminal"}])

(defn not-found-details->api
  [_details]
  [{:field "task_id" :reason "not_found"}])

(def ^:private task-status-string?
  [:enum "started" "finished" "failed" "cancelled"])

(def ^:private span-status-string?
  [:enum "started" "finished" "failed" "cancelled"])

(def ^:private event-status-string?
  [:enum "success" "failure" "timeout" "cancelled" "skipped"])

(def ^:private event-type-string?
  [:enum "llm_call" "tool_call" "retry" "cache"])

(def ^:private span-type-string?
  [:enum "workflow" "agent_step" "llm_call" "tool_call" "retry" "cache" "custom"])

(def TaskDetailResponse
  "Malli schema for GET /v1/tasks/{task_id} 200 response body."
  [:map
   [:task_id uuid-string?]
   [:tenant_id uuid-string?]
   [:external_task_id {:optional true} [:maybe :string]]
   [:workflow {:optional true} [:maybe :string]]
   [:task_type {:optional true} [:maybe :string]]
   [:status task-status-string?]
   [:started_at rfc3339-string?]
   [:finished_at {:optional true} [:maybe rfc3339-string?]]
   [:metadata [:map-of :keyword :any]]
   [:created_at rfc3339-string?]
   [:updated_at rfc3339-string?]])

(def task-detail-response-schema TaskDetailResponse)

(def TaskTraceSummary
  [:map
   [:task_id uuid-string?]
   [:tenant_id uuid-string?]
   [:external_task_id {:optional true} [:maybe :string]]
   [:workflow {:optional true} [:maybe :string]]
   [:task_type {:optional true} [:maybe :string]]
   [:status task-status-string?]
   [:started_at rfc3339-string?]
   [:finished_at {:optional true} [:maybe rfc3339-string?]]
   [:metadata [:map-of :keyword :any]]])

(def SpanTraceResponse
  [:map
   [:span_id uuid-string?]
   [:task_id uuid-string?]
   [:parent_span_id {:optional true} [:maybe uuid-string?]]
   [:external_span_id {:optional true} [:maybe :string]]
   [:span_type span-type-string?]
   [:name {:optional true} [:maybe :string]]
   [:status span-status-string?]
   [:started_at rfc3339-string?]
   [:finished_at {:optional true} [:maybe rfc3339-string?]]
   [:metadata [:map-of :keyword :any]]])

(def EventTraceResponse
  [:map
   [:event_id uuid-string?]
   [:task_id uuid-string?]
   [:span_id {:optional true} [:maybe uuid-string?]]
   [:external_event_id {:optional true} [:maybe :string]]
   [:event_type event-type-string?]
   [:status event-status-string?]
   [:provider {:optional true} [:maybe :string]]
   [:model {:optional true} [:maybe :string]]
   [:tool_name {:optional true} [:maybe :string]]
   [:input_tokens {:optional true} [:maybe int?]]
   [:output_tokens {:optional true} [:maybe int?]]
   [:cached_tokens {:optional true} [:maybe int?]]
   [:latency_ms {:optional true} [:maybe int?]]
   [:retry_count {:optional true} [:maybe int?]]
   [:cache_hit {:optional true} [:maybe :boolean]]
   [:error_code {:optional true} [:maybe :string]]
   [:error_message {:optional true} [:maybe :string]]
   [:metadata [:map-of :keyword :any]]
   [:occurred_at rfc3339-string?]
   [:created_at rfc3339-string?]])

(def TaskTraceResponse
  [:map
   [:task TaskTraceSummary]
   [:spans [:vector SpanTraceResponse]]
   [:events [:vector EventTraceResponse]]])

(def task-trace-response-schema TaskTraceResponse)

(defn parse-task-path-id!
  "Parse task_id from GET path parameters."
  [task-id]
  (try
    (cond
      (instance? UUID task-id) task-id
      (string? task-id) (UUID/fromString task-id)
      :else
      (throw (errors/validation-error
              "Invalid task_id path parameter."
              {:details [(validation-detail "task_id" "invalid_uuid")]})))
    (catch IllegalArgumentException _
      (throw (errors/validation-error
              "Invalid task_id path parameter."
              {:details [(validation-detail "task_id" "invalid_uuid")]})))))

(defn- task-wire-fields
  [task]
  {:task_id (uuid->string (:task/id task))
   :tenant_id (uuid->string (:tenant/id task))
   :external_task_id (:task/external-id task)
   :workflow (:task/workflow task)
   :task_type (:task/type task)
   :status (name (:task/status task))
   :started_at (instant->string (:task/started-at task))
   :finished_at (instant->string (:task/finished-at task))
   :metadata (or (:task/metadata task) {})})

(defn task->detail-response
  [task]
  (assoc (task-wire-fields task)
         :created_at (instant->string (:task/created-at task))
         :updated_at (instant->string (:task/updated-at task))))

(defn task->trace-summary-response
  [task]
  (task-wire-fields task))

(defn span->trace-response
  [span]
  {:span_id (uuid->string (:span/id span))
   :task_id (uuid->string (:task/id span))
   :parent_span_id (uuid->string (:span/parent-id span))
   :external_span_id (:span/external-id span)
   :span_type (name (:span/type span))
   :name (:span/name span)
   :status (name (:span/status span))
   :started_at (instant->string (:span/started-at span))
   :finished_at (instant->string (:span/finished-at span))
   :metadata (or (:span/metadata span) {})})

(defn event->trace-response
  [event]
  {:event_id (uuid->string (:event/id event))
   :task_id (uuid->string (:task/id event))
   :span_id (uuid->string (:span/id event))
   :external_event_id (:event/external-id event)
   :event_type (name (:event/type event))
   :status (name (:event/status event))
   :provider (:event/provider event)
   :model (:event/model event)
   :tool_name (:event/tool-name event)
   :input_tokens (:event/input-tokens event)
   :output_tokens (:event/output-tokens event)
   :cached_tokens (:event/cached-tokens event)
   :latency_ms (:event/latency-ms event)
   :retry_count (:event/retry-count event)
   :cache_hit (:event/cache-hit event)
   :error_code (:event/error-code event)
   :error_message (:event/error-message event)
   :metadata (or (:event/metadata event) {})
   :occurred_at (instant->string (:event/occurred-at event))
   :created_at (instant->string (:event/created-at event))})

(defn validate-detail-response!
  [response]
  (if (m/validate TaskDetailResponse response)
    response
    (throw (ex-info "Invalid task detail response."
                    {:error/kind :internal
                     :error/message "Invalid task detail response."
                     :error/details (explain->details
                                     (m/explain TaskDetailResponse response))}))))

(defn validate-trace-response!
  [response]
  (if (m/validate TaskTraceResponse response)
    response
    (throw (ex-info "Invalid task trace response."
                    {:error/kind :internal
                     :error/message "Invalid task trace response."
                     :error/details (explain->details
                                     (m/explain TaskTraceResponse response))}))))

(defn task-detail-response
  "Build and validate GET /v1/tasks/{task_id} response body."
  [task]
  (-> task task->detail-response validate-detail-response!))

(defn task-trace-response
  "Build and validate GET /v1/tasks/{task_id}/trace response body."
  [{:keys [task spans events]}]
  (-> {:task (task->trace-summary-response task)
       :spans (mapv span->trace-response spans)
       :events (mapv event->trace-response events)}
      validate-trace-response!))
