;; SPDX-FileCopyrightText: 2026 Haluan Irsad
;; SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Commercial

(ns token-taper.event.api-schema
  (:require
   [clojure.string :as str]
   [malli.core :as m]
   [malli.error :as me]
   [token-taper.task.api-schema :as task-api-schema]
   [token-taper.task.errors :as errors]
   [token-taper.time.instant :as time-instant])
  (:import
   [java.time Instant]
   [java.util UUID]))

(def ^:private non-blank-string?
  [:and string? [:fn {:error/message "must be non-blank"} #(not (str/blank? %))]])

(def ^:private non-neg-int?
  [:int {:min 0}])

(def ^:private uuid-string?
  [:re #"^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"])

(def ^:private rfc3339-string?
  [:re #"^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,9})?(?:Z|[+-]\d{2}:\d{2})$"])

(def ^:private event-status-string?
  [:enum "success" "failure" "timeout" "cancelled" "skipped"])

(def ^:private event-status?
  [:enum :success :failure :timeout :cancelled :skipped])

(def LlmCallEventRequest
  [:map
   [:tenant_id uuid?]
   [:task_id uuid?]
   [:provider non-blank-string?]
   [:model non-blank-string?]
   [:input_tokens non-neg-int?]
   [:output_tokens non-neg-int?]
   [:latency_ms non-neg-int?]
   [:status event-status?]
   [:span_id {:optional true} [:maybe uuid?]]
   [:external_event_id {:optional true} [:maybe :string]]
   [:cached_tokens {:optional true} [:maybe non-neg-int?]]
   [:error_code {:optional true} [:maybe :string]]
   [:error_message {:optional true} [:maybe :string]]
   [:occurred_at {:optional true} [:maybe inst?]]
   [:metadata {:optional true} [:map-of :keyword :any]]])

(def LlmCallEventResponse
  [:map
   [:event_id uuid-string?]
   [:tenant_id uuid-string?]
   [:task_id uuid-string?]
   [:span_id {:optional true} [:maybe uuid-string?]]
   [:external_event_id {:optional true} [:maybe :string]]
   [:event_type [:= "llm_call"]]
   [:provider :string]
   [:model :string]
   [:input_tokens non-neg-int?]
   [:output_tokens non-neg-int?]
   [:cached_tokens {:optional true} [:maybe non-neg-int?]]
   [:latency_ms non-neg-int?]
   [:status event-status-string?]
   [:error_code {:optional true} [:maybe :string]]
   [:error_message {:optional true} [:maybe :string]]
   [:occurred_at rfc3339-string?]
   [:metadata [:map-of :keyword :any]]])

(def llm-call-event-response-schema LlmCallEventResponse)

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

(defn- ->event-status
  [value]
  (cond
    (keyword? value) value
    (string? value) (keyword value)
    :else value))

(defn normalize-llm-call-request
  [input]
  (cond-> (-> input
              (update :tenant_id ->uuid)
              (update :task_id ->uuid)
              (update :status ->event-status)
              (update :metadata #(or % {})))
    (:span_id input) (update :span_id ->uuid)
    (contains? input :occurred_at)
    (update :occurred_at #(time-instant/coerce-instant-field!
                          "occurred_at"
                          %
                          "Invalid LLM call event request."))))

(defn validate-llm-call-request!
  [input]
  (let [normalized (-> input
                       (update :metadata #(or % {}))
                       normalize-llm-call-request)]
    (if (m/validate LlmCallEventRequest normalized)
      normalized
      (throw (errors/validation-error
              "Invalid LLM call event request."
              {:details (explain->details (m/explain LlmCallEventRequest normalized))})))))

(defn llm-call-request->create-input
  [validated]
  (assoc validated
         :event_type :llm_call
         :occurred_at (or (:occurred_at validated) (Instant/now))))

(defn- instant->string
  [value]
  (when value (str value)))

(defn- uuid->string
  [value]
  (when value (str value)))

(defn event->llm-call-response
  [event]
  {:event_id (uuid->string (:event/id event))
   :tenant_id (uuid->string (:tenant/id event))
   :task_id (uuid->string (:task/id event))
   :span_id (uuid->string (:span/id event))
   :external_event_id (:event/external-id event)
   :event_type "llm_call"
   :provider (:event/provider event)
   :model (:event/model event)
   :input_tokens (:event/input-tokens event)
   :output_tokens (:event/output-tokens event)
   :cached_tokens (:event/cached-tokens event)
   :latency_ms (:event/latency-ms event)
   :status (name (:event/status event))
   :error_code (:event/error-code event)
   :error_message (:event/error-message event)
   :occurred_at (instant->string (:event/occurred-at event))
   :metadata (or (:event/metadata event) {})})

(defn validate-llm-call-response!
  [response]
  (if (m/validate LlmCallEventResponse response)
    response
    (throw (ex-info "Invalid LLM call event response."
                    {:error/kind :internal
                     :error/message "Invalid LLM call event response."
                     :error/details (explain->details
                                     (m/explain LlmCallEventResponse response))}))))

(defn llm-call-event-response
  "Build and validate POST /v1/events/llm-call 201 response body."
  [event]
  (-> event event->llm-call-response validate-llm-call-response!))

(defn validation-details->api
  [details]
  (task-api-schema/validation-details->api details))

(defn task-not-found-details->api
  [_details]
  [{:field "task_id" :reason "not_found"}])

(defn span-not-found-details->api
  [_details]
  [{:field "span_id" :reason "not_found"}])

(defn event-conflict-details->api
  [_details]
  [{:field "external_event_id" :reason "duplicate"}])
