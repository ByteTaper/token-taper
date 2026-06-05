;; SPDX-FileCopyrightText: 2026 Haluan Irsad
;; SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Commercial

(ns token-taper.event.repository
  (:require
   [jsonista.core :as json]
   [token-taper.db.jdbc :as jdbc]
   [token-taper.task.errors :as errors]
   [token-taper.task.repository :as task-repo]
   [token-taper.trace.span-repository :as span-repo]
   [token-taper.event.model :as model]
   [token-taper.event.schema :as schema])
  (:import
   [java.sql Timestamp]
   [java.time Instant]
   [java.util UUID]))

(defn- encode-metadata
  [metadata]
  (json/write-value-as-string (or metadata {})))

(defn- ->timestamp
  [value]
  (cond
    (nil? value) nil
    (instance? Instant value) (Timestamp/from ^Instant value)
    (instance? Timestamp value) value
    :else value))

(defn- select-event-sql
  []
  "SELECT id, tenant_id, task_id, span_id, external_event_id, event_type, status,
          provider, model, tool_name, input_tokens, output_tokens, cached_tokens,
          latency_ms, retry_count, cache_hit, error_code, error_message, metadata,
          occurred_at, created_at
   FROM ai_event")

(defn- order-by-trace
  []
  " ORDER BY occurred_at ASC, created_at ASC")

(defn- rows->events
  [rows]
  (mapv model/row->event rows))

(defn find-event-by-id
  [db event-id]
  (some-> (jdbc/execute-one!
           db
           [(str (select-event-sql) " WHERE id = ?")
            event-id])
          model/row->event))

(defn find-event-by-external-id
  [db tenant-id external-event-id]
  (some-> (jdbc/execute-one!
           db
           [(str (select-event-sql)
                 " WHERE tenant_id = ? AND external_event_id = ?")
            tenant-id
            external-event-id])
          model/row->event))

(defn find-events-by-task-id
  [db task-id]
  (rows->events
   (jdbc/execute!
    db
    [(str (select-event-sql) " WHERE task_id = ?" (order-by-trace))
     task-id])))

(defn find-events-by-span-id
  [db span-id]
  (rows->events
   (jdbc/execute!
    db
    [(str (select-event-sql) " WHERE span_id = ?" (order-by-trace))
     span-id])))

(defn find-events-by-task-id-and-type
  [db task-id event-type]
  (rows->events
   (jdbc/execute!
    db
    [(str (select-event-sql)
          " WHERE task_id = ? AND event_type = ?"
          (order-by-trace))
     task-id
     (model/event-type->db event-type)])))

(defn- unique-violation?
  [e]
  (and (instance? org.postgresql.util.PSQLException e)
       (= "23505" (.getSQLState ^org.postgresql.util.PSQLException e))))

(defn- validate-span-for-event!
  [db task-id span-id]
  (let [span (span-repo/find-span-by-id db span-id)]
    (when (nil? span)
      (throw (errors/not-found-error
              "Span not found"
              {:details {:span_id span-id}})))
    (when-not (= task-id (:task/id span))
      (throw (errors/validation-error
              "Span belongs to another task"
              {:details {:task_id task-id
                        :span_id span-id
                        :span_task_id (:task/id span)}})))
    span))

(defn- validate-task-for-event!
  [db tenant-id task-id]
  (let [task (task-repo/find-task-by-id db task-id)]
    (when (nil? task)
      (throw (errors/not-found-error
              "Task not found"
              {:details {:task_id task-id}})))
    (when-not (= tenant-id (:tenant/id task))
      (throw (errors/validation-error
              "tenant_id does not match task"
              {:details {:tenant_id tenant-id
                        :task_id task-id
                        :task_tenant_id (:tenant/id task)}})))
    task))

(defn create-event!
  [db input]
  (let [validated (schema/validate-create-input! input)
        tenant-id (:tenant_id validated)
        task-id (:task_id validated)]
    (validate-task-for-event! db tenant-id task-id)
    (when-let [span-id (:span_id validated)]
      (validate-span-for-event! db task-id span-id))
    (let [event-id (UUID/randomUUID)
          occurred-at (->timestamp (:occurred_at validated))]
      (try
        (let [row (jdbc/execute-one!
                   db
                   ["INSERT INTO ai_event (
                       id, tenant_id, task_id, span_id, external_event_id,
                       event_type, status, provider, model, tool_name,
                       input_tokens, output_tokens, cached_tokens,
                       latency_ms, retry_count, cache_hit,
                       error_code, error_message, metadata, occurred_at
                     ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?)
                     RETURNING id, tenant_id, task_id, span_id, external_event_id,
                               event_type, status, provider, model, tool_name,
                               input_tokens, output_tokens, cached_tokens,
                               latency_ms, retry_count, cache_hit,
                               error_code, error_message, metadata,
                               occurred_at, created_at"
                    event-id
                    tenant-id
                    task-id
                    (:span_id validated)
                    (:external_event_id validated)
                    (model/event-type->db (:event_type validated))
                    (model/status->db (:status validated))
                    (:provider validated)
                    (:model validated)
                    (:tool_name validated)
                    (:input_tokens validated)
                    (:output_tokens validated)
                    (:cached_tokens validated)
                    (:latency_ms validated)
                    (:retry_count validated)
                    (:cache_hit validated)
                    (:error_code validated)
                    (:error_message validated)
                    (encode-metadata (:metadata validated))
                    occurred-at])]
          (-> row model/row->event model/validate-non-negative-counts!))
        (catch org.postgresql.util.PSQLException e
          (if (unique-violation? e)
            (throw (errors/conflict-error
                    "Duplicate external_event_id for tenant"
                    {:details {:tenant_id tenant-id
                               :external_event_id (:external_event_id validated)}}))
            (throw e)))))))
