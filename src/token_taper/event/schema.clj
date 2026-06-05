;; SPDX-FileCopyrightText: 2026 Haluan Irsad
;; SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Commercial

(ns token-taper.event.schema
  (:require
   [clojure.string :as str]
   [malli.core :as m]
   [malli.error :as me]
   [token-taper.task.errors :as errors]
   [token-taper.event.model :as model]))

(def non-neg-int?
  [:int {:min 0}])

(def CreateEventInput
  [:map
   [:tenant_id uuid?]
   [:task_id uuid?]
   [:event_type [:enum :llm_call :tool_call :retry :cache]]
   [:status [:enum :success :failure :timeout :cancelled :skipped]]
   [:occurred_at :any]
   [:span_id {:optional true} [:maybe uuid?]]
   [:external_event_id {:optional true} [:maybe string?]]
   [:provider {:optional true} [:maybe string?]]
   [:model {:optional true} [:maybe string?]]
   [:tool_name {:optional true} [:maybe string?]]
   [:input_tokens {:optional true} [:maybe non-neg-int?]]
   [:output_tokens {:optional true} [:maybe non-neg-int?]]
   [:cached_tokens {:optional true} [:maybe non-neg-int?]]
   [:latency_ms {:optional true} [:maybe non-neg-int?]]
   [:retry_count {:optional true} [:maybe non-neg-int?]]
   [:cache_hit {:optional true} [:maybe :boolean]]
   [:error_code {:optional true} [:maybe string?]]
   [:error_message {:optional true} [:maybe string?]]
   [:metadata {:optional true} [:map-of :keyword :any]]])

(defn- explain->message
  [explain]
  (some-> explain me/humanize pr-str))

(defn- present?
  [v]
  (cond
    (string? v) (not (str/blank? v))
    (number? v) true
    (boolean? v) true
    :else (some? v)))

(defn validate-event-type!
  [event-type]
  (when-not (model/valid-event-type? event-type)
    (throw (errors/validation-error
            "Invalid event type"
            {:details {:event_type event-type}}))))

(defn validate-status!
  [status]
  (when-not (model/valid-status? status)
    (throw (errors/validation-error
            "Invalid event status"
            {:details {:status status}}))))

(defn validate-event-type-fields!
  [input]
  (case (:event_type input)
    :llm_call
    (when (or (not (present? (:provider input)))
              (not (present? (:model input)))
              (not (present? (:input_tokens input)))
              (not (present? (:output_tokens input)))
              (not (present? (:latency_ms input))))
      (throw (errors/validation-error
              "LLM call event requires provider, model, input_tokens, output_tokens, and latency_ms"
              {:details {:event_type :llm_call}})))

    :tool_call
    (when (or (not (present? (:tool_name input)))
              (not (present? (:latency_ms input))))
      (throw (errors/validation-error
              "Tool call event requires tool_name and latency_ms"
              {:details {:event_type :tool_call}})))

    :retry
    (when (or (not (present? (:retry_count input)))
              (not (contains? (:metadata input) :reason)))
      (throw (errors/validation-error
              "Retry event requires retry_count and metadata.reason"
              {:details {:event_type :retry}})))

    :cache
    (when (nil? (:cache_hit input))
      (throw (errors/validation-error
              "Cache event requires cache_hit"
              {:details {:event_type :cache}})))

    nil)
  input)

(defn validate-create-input!
  [input]
  (if (m/validate CreateEventInput input)
    (let [with-metadata (assoc input :metadata (or (:metadata input) {}))]
      (validate-event-type-fields! with-metadata))
    (throw (errors/validation-error
            "Invalid event creation input"
            {:details (explain->message (m/explain CreateEventInput input))}))))
