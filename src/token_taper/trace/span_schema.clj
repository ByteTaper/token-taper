;; SPDX-FileCopyrightText: 2026 Haluan Irsad
;; SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Commercial

(ns token-taper.trace.span-schema
  (:require
   [malli.core :as m]
   [malli.error :as me]
   [token-taper.task.errors :as errors]
   [token-taper.trace.span-model :as model]
   [token-taper.time.instant :as time-instant])
  (:import
   [java.time Instant]))

(def CreateSpanInput
  [:map
   [:tenant_id uuid?]
   [:task_id uuid?]
   [:span_type [:enum :workflow :agent_step :llm_call :tool_call :retry :cache :custom]]
   [:parent_span_id {:optional true} [:maybe uuid?]]
   [:external_span_id {:optional true} [:maybe string?]]
   [:name {:optional true} [:maybe string?]]
   [:metadata {:optional true} [:map-of :keyword :any]]
   [:started_at {:optional true} [:maybe inst?]]])

(def FinishSpanInput
  [:map
   [:status [:enum :finished :failed :cancelled]]
   [:finished_at inst?]
   [:metadata {:optional true} [:map-of :keyword :any]]])

(defn- explain->message
  [explain]
  (some-> explain me/humanize pr-str))

(defn- normalize-create-input
  [input]
  (cond-> (-> input (update :metadata #(or % {})))
    (contains? input :started_at)
    (update :started_at #(time-instant/coerce-instant-field!
                          "started_at"
                          %
                          "Invalid span creation input"))))

(defn validate-create-input!
  [input]
  (let [normalized (normalize-create-input input)]
    (if (m/validate CreateSpanInput normalized)
      normalized
      (throw (errors/validation-error
              "Invalid span creation input"
              {:details (explain->message (m/explain CreateSpanInput normalized))})))))

(defn- normalize-finish-input
  [input]
  (-> input
      (update :metadata #(or % {}))
      (update :finished_at #(time-instant/require-instant-field!
                             "finished_at"
                             %
                             "Invalid span finish input"))))

(defn validate-finish-input!
  [input]
  (let [normalized (normalize-finish-input input)]
    (if (m/validate FinishSpanInput normalized)
      normalized
      (throw (errors/validation-error
              "Invalid span finish input"
              {:details (explain->message (m/explain FinishSpanInput normalized))})))))

(defn validate-status!
  [status]
  (when-not (model/valid-status? status)
    (throw (errors/validation-error
            "Invalid span status"
            {:details {:status status}}))))

(defn validate-span-type!
  [span-type]
  (when-not (model/valid-span-type? span-type)
    (throw (errors/validation-error
            "Invalid span type"
            {:details {:span_type span-type}}))))
