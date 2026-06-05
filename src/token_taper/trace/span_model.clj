;; SPDX-FileCopyrightText: 2026 Haluan Irsad
;; SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Commercial

(ns token-taper.trace.span-model
  (:require
   [clojure.string :as str]
   [jsonista.core :as json])
  (:import
   [java.sql Timestamp]
   [org.postgresql.util PGobject]))

(def ^:private metadata-mapper
  (json/object-mapper {:decode-key-fn keyword}))

(def span-statuses
  #{:started :finished :failed :cancelled})

(def terminal-statuses
  #{:finished :failed :cancelled})

(def span-types
  #{:workflow :agent_step :llm_call :tool_call :retry :cache :custom})

(def db-statuses
  #{"started" "finished" "failed" "cancelled"})

(def db-span-types
  #{"workflow" "agent_step" "llm_call" "tool_call" "retry" "cache" "custom"})

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

(defn span-type->db
  [span-type]
  (name span-type))

(defn db->span-type
  [span-type]
  (keyword span-type))

(defn valid-status?
  [status]
  (contains? span-statuses status))

(defn valid-span-type?
  [span-type]
  (contains? span-types span-type))

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

(defn row->span
  [row]
  (when row
    {:span/id (:id row)
     :tenant/id (:tenant-id row)
     :task/id (:task-id row)
     :span/parent-id (:parent-span-id row)
     :span/external-id (:external-span-id row)
     :span/type (db->span-type (:span-type row))
     :span/name (:name row)
     :span/status (db->status (:status row))
     :span/started-at (->instant (:started-at row))
     :span/finished-at (->instant (:finished-at row))
     :span/metadata (parse-metadata (:metadata row))
     :span/created-at (->instant (:created-at row))
     :span/updated-at (->instant (:updated-at row))}))

(defn validate-span-invariants!
  [span]
  (let [status (:span/status span)
        finished-at (:span/finished-at span)]
    (cond
      (not (valid-status? status))
      (throw (ex-info "Invalid span status"
                      {:error/kind :validation
                       :error/message "Invalid span status"
                       :error/details {:status status}}))

      (and (started? status) finished-at)
      (throw (ex-info "Started span must not have finished_at"
                      {:error/kind :validation
                       :error/message "Started span must not have finished_at"}))

      (and (terminal? status) (nil? finished-at))
      (throw (ex-info "Terminal span must have finished_at"
                      {:error/kind :validation
                       :error/message "Terminal span must have finished_at"}))

      :else span)))

(defn validate-finish-transition!
  [span new-status]
  (when (terminal? (:span/status span))
    (throw (ex-info "Span is already terminal"
                    {:error/kind :conflict
                     :error/message "Span is already terminal"
                     :error/details {:span-id (:span/id span)
                                     :status (:span/status span)}})))
  (when-not (started? (:span/status span))
    (throw (ex-info "Span is not in started status"
                    {:error/kind :conflict
                     :error/message "Span is not in started status"
                     :error/details {:span-id (:span/id span)
                                     :status (:span/status span)}})))
  (when-not (terminal? new-status)
    (throw (ex-info "Finish status must be terminal"
                    {:error/kind :validation
                     :error/message "Finish status must be terminal"
                     :error/details {:status new-status}})))
  span)

(defn merge-metadata
  [existing incoming]
  (if (nil? incoming)
    (or existing {})
    (merge (or existing {}) incoming)))
