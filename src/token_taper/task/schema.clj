;; SPDX-FileCopyrightText: 2026 Haluan Irsad
;; SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Commercial

(ns token-taper.task.schema
  (:require
   [malli.core :as m]
   [malli.error :as me]
   [token-taper.task.errors :as errors]
   [token-taper.task.model :as model]))

(def CreateTaskInput
  [:map
   [:tenant_id uuid?]
   [:external_task_id {:optional true} [:maybe string?]]
   [:workflow {:optional true} [:maybe string?]]
   [:task_type {:optional true} [:maybe string?]]
   [:metadata {:optional true} [:map-of :keyword :any]]
   [:started_at {:optional true} :any]])

(def FinishTaskInput
  [:map
   [:status [:enum :finished :failed :cancelled]]
   [:finished_at :any]
   [:metadata {:optional true} [:map-of :keyword :any]]])

(defn- explain->message
  [explain]
  (some-> explain me/humanize pr-str))

(defn validate-create-input!
  [input]
  (if (m/validate CreateTaskInput input)
    (assoc input :metadata (or (:metadata input) {}))
    (throw (errors/validation-error
            "Invalid task creation input"
            {:details (explain->message (m/explain CreateTaskInput input))}))))

(defn validate-finish-input!
  [input]
  (if (m/validate FinishTaskInput input)
    input
    (throw (errors/validation-error
            "Invalid task finish input"
            {:details (explain->message (m/explain FinishTaskInput input))}))))

(defn validate-status!
  [status]
  (when-not (model/valid-status? status)
    (throw (errors/validation-error
            "Invalid task status"
            {:details {:status status}}))))
