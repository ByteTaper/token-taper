;; SPDX-FileCopyrightText: 2026 Haluan Irsad
;; SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Commercial

(ns token-taper.task.model
  (:require
   [clojure.string :as str]
   [jsonista.core :as json])
  (:import
   [java.sql Timestamp]
   [org.postgresql.util PGobject]))

(def ^:private metadata-mapper
  (json/object-mapper {:decode-key-fn keyword}))

(def task-statuses
  #{:started :finished :failed :cancelled})

(def terminal-statuses
  #{:finished :failed :cancelled})

(def db-statuses
  #{"started" "finished" "failed" "cancelled"})

(defn terminal?
  [status]
  (contains? terminal-statuses status))

(defn started?
  [status]
  (= :started status))

(defn status->db
  [status]
  (name status))

(defn db->status
  [status]
  (keyword status))

(defn valid-status?
  [status]
  (contains? task-statuses status))

(defn- parse-metadata
  [value]
  (cond
    (nil? value) {}
    (map? value) value
    (instance? PGobject value)
    (let [^PGobject pg value]
      (if (str/blank? (.getValue pg))
        {}
        (json/read-value (.getValue pg) metadata-mapper)))
    (string? value)
    (if (str/blank? value)
      {}
      (json/read-value value metadata-mapper))
    :else {}))

(defn- ->instant
  [value]
  (cond
    (nil? value) nil
    (instance? java.time.Instant value) value
    (instance? java.time.OffsetDateTime value) (.toInstant ^java.time.OffsetDateTime value)
    (instance? java.time.LocalDateTime value)
    (.toInstant (.atZone ^java.time.LocalDateTime value (java.time.ZoneOffset/UTC)))
    (instance? Timestamp value) (.toInstant ^Timestamp value)
    :else value))

(defn row->task
  [row]
  (when row
    {:task/id (:id row)
     :tenant/id (:tenant-id row)
     :task/external-id (:external-task-id row)
     :task/workflow (:workflow row)
     :task/type (:task-type row)
     :task/status (db->status (:status row))
     :task/started-at (->instant (:started-at row))
     :task/finished-at (->instant (:finished-at row))
     :task/metadata (parse-metadata (:metadata row))
     :task/created-at (->instant (:created-at row))
     :task/updated-at (->instant (:updated-at row))}))

(defn task->row
  [task]
  {:id (:task/id task)
   :tenant-id (:tenant/id task)
   :external-task-id (:task/external-id task)
   :workflow (:task/workflow task)
   :task-type (:task/type task)
   :status (status->db (:task/status task))
   :started-at (:task/started-at task)
   :finished-at (:task/finished-at task)
   :metadata (:task/metadata task)})

(defn validate-task-invariants!
  [task]
  (let [status (:task/status task)
        finished-at (:task/finished-at task)]
    (cond
      (not (valid-status? status))
      (throw (ex-info "Invalid task status"
                      {:error/kind :validation
                       :error/message "Invalid task status"
                       :error/details {:status status}}))

      (and (started? status) finished-at)
      (throw (ex-info "Started task must not have finished_at"
                      {:error/kind :validation
                       :error/message "Started task must not have finished_at"}))

      (and (terminal? status) (nil? finished-at))
      (throw (ex-info "Terminal task must have finished_at"
                      {:error/kind :validation
                       :error/message "Terminal task must have finished_at"}))

      :else task)))

(defn validate-finish-transition!
  [task new-status]
  (when (terminal? (:task/status task))
    (throw (ex-info "Task is already terminal"
                    {:error/kind :conflict
                     :error/message "Task is already terminal"
                     :error/details {:task-id (:task/id task)
                                     :status (:task/status task)}})))
  (when-not (started? (:task/status task))
    (throw (ex-info "Task is not in started status"
                    {:error/kind :conflict
                     :error/message "Task is not in started status"
                     :error/details {:task-id (:task/id task)
                                     :status (:task/status task)}})))
  (when-not (terminal? new-status)
    (throw (ex-info "Finish status must be terminal"
                    {:error/kind :validation
                     :error/message "Finish status must be terminal"
                     :error/details {:status new-status}})))
  task)

(defn merge-metadata
  [existing incoming]
  (if (nil? incoming)
    (or existing {})
    (merge (or existing {}) incoming)))
