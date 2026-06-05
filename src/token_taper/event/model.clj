;; SPDX-FileCopyrightText: 2026 Haluan Irsad
;; SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Commercial

(ns token-taper.event.model
  (:require
   [clojure.string :as str]
   [jsonista.core :as json])
  (:import
   [java.sql Timestamp]
   [org.postgresql.util PGobject]))

(def ^:private metadata-mapper
  (json/object-mapper {:decode-key-fn keyword}))

(def event-types
  #{:llm_call :tool_call :retry :cache})

(def event-statuses
  #{:success :failure :timeout :cancelled :skipped})

(def db-event-types
  #{"llm_call" "tool_call" "retry" "cache"})

(def db-statuses
  #{"success" "failure" "timeout" "cancelled" "skipped"})

(defn event-type->db
  [event-type]
  (name event-type))

(defn db->event-type
  [event-type]
  (keyword event-type))

(defn status->db
  [status]
  (name status))

(defn db->status
  [status]
  (keyword status))

(defn valid-event-type?
  [event-type]
  (contains? event-types event-type))

(defn valid-status?
  [status]
  (contains? event-statuses status))

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

(defn row->event
  [row]
  (when row
    {:event/id (:id row)
     :tenant/id (:tenant-id row)
     :task/id (:task-id row)
     :span/id (:span-id row)
     :event/external-id (:external-event-id row)
     :event/type (db->event-type (:event-type row))
     :event/status (db->status (:status row))
     :event/provider (:provider row)
     :event/model (:model row)
     :event/tool-name (:tool-name row)
     :event/input-tokens (:input-tokens row)
     :event/output-tokens (:output-tokens row)
     :event/cached-tokens (:cached-tokens row)
     :event/latency-ms (:latency-ms row)
     :event/retry-count (:retry-count row)
     :event/cache-hit (:cache-hit row)
     :event/error-code (:error-code row)
     :event/error-message (:error-message row)
     :event/metadata (parse-metadata (:metadata row))
     :event/occurred-at (->instant (:occurred-at row))
     :event/created-at (->instant (:created-at row))}))

(defn- negative?
  [n]
  (and (some? n) (neg? (long n))))

(defn validate-non-negative-counts!
  [event]
  (cond
    (negative? (:event/input-tokens event))
    (throw (ex-info "input_tokens must not be negative"
                    {:error/kind :validation
                     :error/message "input_tokens must not be negative"}))

    (negative? (:event/output-tokens event))
    (throw (ex-info "output_tokens must not be negative"
                    {:error/kind :validation
                     :error/message "output_tokens must not be negative"}))

    (negative? (:event/cached-tokens event))
    (throw (ex-info "cached_tokens must not be negative"
                    {:error/kind :validation
                     :error/message "cached_tokens must not be negative"}))

    (negative? (:event/latency-ms event))
    (throw (ex-info "latency_ms must not be negative"
                    {:error/kind :validation
                     :error/message "latency_ms must not be negative"}))

    (negative? (:event/retry-count event))
    (throw (ex-info "retry_count must not be negative"
                    {:error/kind :validation
                     :error/message "retry_count must not be negative"}))

    :else event))

(defn validate-create-input-counts!
  "Validate token/latency counters on create input before persistence."
  [input]
  (validate-non-negative-counts!
   {:event/input-tokens (:input_tokens input)
    :event/output-tokens (:output_tokens input)
    :event/cached-tokens (:cached_tokens input)
    :event/latency-ms (:latency_ms input)
    :event/retry-count (:retry_count input)}))
