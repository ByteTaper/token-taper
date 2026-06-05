;; SPDX-FileCopyrightText: 2026 Haluan Irsad
;; SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Commercial

(ns token-taper.trace.span-repository
  (:require
   [jsonista.core :as json]
   [token-taper.db.jdbc :as jdbc]
   [token-taper.task.errors :as errors]
   [token-taper.task.repository :as task-repo]
   [token-taper.trace.span-model :as model]
   [token-taper.trace.span-schema :as schema])
  (:import
   [java.sql Timestamp]
   [java.time Instant]
   [java.util UUID]))

(defn- encode-metadata
  [metadata]
  (json/write-value-as-string (or metadata {})))

(defn- now
  []
  (Instant/now))

(defn- ->timestamp
  [value]
  (cond
    (nil? value) nil
    (instance? Instant value) (Timestamp/from ^Instant value)
    (instance? Timestamp value) value
    :else value))

(defn- select-span-sql
  []
  "SELECT id, tenant_id, task_id, parent_span_id, external_span_id, span_type, name,
          status, started_at, finished_at, metadata, created_at, updated_at
   FROM ai_span")

(defn- order-by-trace
  []
  " ORDER BY started_at ASC, created_at ASC")

(defn- rows->spans
  [rows]
  (mapv model/row->span rows))

(defn find-span-by-id
  [db span-id]
  (some-> (jdbc/execute-one!
           db
           [(str (select-span-sql) " WHERE id = ?")
            span-id])
          model/row->span))

(defn find-span-by-external-id
  [db tenant-id external-span-id]
  (some-> (jdbc/execute-one!
           db
           [(str (select-span-sql)
                 " WHERE tenant_id = ? AND external_span_id = ?")
            tenant-id
            external-span-id])
          model/row->span))

(defn find-spans-by-task-id
  [db task-id]
  (rows->spans
   (jdbc/execute!
    db
    [(str (select-span-sql) " WHERE task_id = ?" (order-by-trace))
     task-id])))

(defn find-child-spans
  [db parent-span-id]
  (rows->spans
   (jdbc/execute!
    db
    [(str (select-span-sql) " WHERE parent_span_id = ?" (order-by-trace))
     parent-span-id])))

(defn- unique-violation?
  [e]
  (and (instance? org.postgresql.util.PSQLException e)
       (= "23505" (.getSQLState ^org.postgresql.util.PSQLException e))))

(defn- validate-parent-span!
  [db task-id parent-span-id]
  (let [parent (find-span-by-id db parent-span-id)]
    (when (nil? parent)
      (throw (errors/not-found-error
              "Parent span not found"
              {:details {:parent_span_id parent-span-id}})))
    (when-not (= task-id (:task/id parent))
      (throw (errors/validation-error
              "Parent span belongs to another task"
              {:details {:task_id task-id
                        :parent_span_id parent-span-id
                        :parent_task_id (:task/id parent)}})))
    parent))

(defn- validate-task-for-span!
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

(defn create-span!
  [db input]
  (let [validated (schema/validate-create-input! input)
        tenant-id (:tenant_id validated)
        task-id (:task_id validated)]
    (validate-task-for-span! db tenant-id task-id)
    (when-let [parent-id (:parent_span_id validated)]
      (validate-parent-span! db task-id parent-id))
    (let [span-id (UUID/randomUUID)
          started-at (->timestamp (or (:started_at validated) (now)))]
      (try
        (let [row (jdbc/execute-one!
                   db
                   ["INSERT INTO ai_span (
                       id, tenant_id, task_id, parent_span_id, external_span_id,
                       span_type, name, status, started_at, finished_at, metadata
                     ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, ?::jsonb)
                     RETURNING id, tenant_id, task_id, parent_span_id, external_span_id,
                               span_type, name, status, started_at, finished_at, metadata,
                               created_at, updated_at"
                    span-id
                    tenant-id
                    task-id
                    (:parent_span_id validated)
                    (:external_span_id validated)
                    (model/span-type->db (:span_type validated))
                    (:name validated)
                    "started"
                    started-at
                    (encode-metadata (:metadata validated))])]
          (-> row model/row->span model/validate-span-invariants!))
        (catch org.postgresql.util.PSQLException e
          (if (unique-violation? e)
            (throw (errors/conflict-error
                    "Duplicate external_span_id for tenant"
                    {:details {:tenant_id tenant-id
                               :external_span_id (:external_span_id validated)}}))
            (throw e)))))))

(defn update-span-status!
  [db span-id status finished-at & {:keys [metadata]}]
  (schema/validate-status! status)
  (let [finished-at' (->timestamp (if (model/started? status) nil finished-at))
        row (jdbc/execute-one!
             db
             ["UPDATE ai_span
               SET status = ?, finished_at = ?, metadata = ?::jsonb, updated_at = now()
               WHERE id = ?
               RETURNING id, tenant_id, task_id, parent_span_id, external_span_id,
                         span_type, name, status, started_at, finished_at, metadata,
                         created_at, updated_at"
              (model/status->db status)
              finished-at'
              (encode-metadata (or metadata {}))
              span-id])]
    (when-not row
      (throw (errors/not-found-error "Span not found" {:details {:span-id span-id}})))
    (-> row model/row->span model/validate-span-invariants!)))

(defn finish-span!
  [db span-id finish-data]
  (let [validated (schema/validate-finish-input! finish-data)
        span (find-span-by-id db span-id)]
    (when (nil? span)
      (throw (errors/not-found-error "Span not found" {:details {:span-id span-id}})))
    (model/validate-finish-transition! span (:status validated))
    (let [finished-at (->timestamp (:finished_at validated))
          metadata (model/merge-metadata (:span/metadata span) (:metadata validated))
          row (jdbc/execute-one!
               db
               ["UPDATE ai_span
                 SET status = ?, finished_at = ?, metadata = ?::jsonb, updated_at = now()
                 WHERE id = ? AND status = 'started'
                 RETURNING id, tenant_id, task_id, parent_span_id, external_span_id,
                           span_type, name, status, started_at, finished_at, metadata,
                           created_at, updated_at"
                (model/status->db (:status validated))
                finished-at
                (encode-metadata metadata)
                span-id])]
      (when-not row
        (throw (errors/conflict-error
                "Span is not in started status"
                {:details {:span-id span-id :status (:span/status span)}})))
      (-> row model/row->span model/validate-span-invariants!))))
