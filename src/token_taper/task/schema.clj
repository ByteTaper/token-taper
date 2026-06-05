;; SPDX-FileCopyrightText: 2026 Haluan Irsad
;; SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Commercial

(ns token-taper.task.schema
  (:require
   [malli.core :as m]
   [malli.error :as me]
   [token-taper.task.errors :as errors]
   [token-taper.task.model :as model]
   [token-taper.time.instant :as time-instant])
  (:import
   [java.time Instant]))

(def CreateTaskInput
  [:map
   [:tenant_id uuid?]
   [:external_task_id {:optional true} [:maybe string?]]
   [:workflow {:optional true} [:maybe string?]]
   [:task_type {:optional true} [:maybe string?]]
   [:metadata {:optional true} [:map-of :keyword :any]]
   [:started_at {:optional true} [:maybe inst?]]])

(def FinishTaskInput
  [:map
   [:status [:enum :finished :failed :cancelled]]
   [:finished_at {:optional true} [:maybe inst?]]
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
                          "Invalid task creation input"))))

(defn validate-create-input!
  [input]
  (let [normalized (normalize-create-input input)]
    (if (m/validate CreateTaskInput normalized)
      normalized
      (throw (errors/validation-error
              "Invalid task creation input"
              {:details (explain->message (m/explain CreateTaskInput normalized))})))))

(defn- normalize-finish-input
  [input]
  (cond-> (-> input (update :metadata #(or % {})))
    (contains? input :finished_at)
    (update :finished_at #(time-instant/coerce-instant-field!
                           "finished_at"
                           %
                           "Invalid task finish input"))))

(defn validate-finish-input!
  [input]
  (let [normalized (normalize-finish-input input)]
    (if (m/validate FinishTaskInput normalized)
      normalized
      (throw (errors/validation-error
              "Invalid task finish input"
              {:details (explain->message (m/explain FinishTaskInput normalized))})))))

(defn validate-status!
  [status]
  (when-not (model/valid-status? status)
    (throw (errors/validation-error
            "Invalid task status"
            {:details {:status status}}))))
