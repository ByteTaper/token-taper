;; SPDX-FileCopyrightText: 2026 Haluan Irsad
;; SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Commercial

(ns token-taper.task.repository
  (:require
   [jsonista.core :as json]
   [token-taper.db.jdbc :as jdbc]
   [token-taper.task.errors :as errors]
   [token-taper.task.model :as model]
   [token-taper.task.schema :as schema])
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

(defn- select-task-sql
  []
  "SELECT id, tenant_id, external_task_id, workflow, task_type,
          status, started_at, finished_at, metadata, created_at, updated_at
   FROM ai_task")

(defn find-task-by-id
  [db task-id]
  (some-> (jdbc/execute-one!
           db
           [(str (select-task-sql) " WHERE id = ?")
            task-id])
          model/row->task))

(defn find-task-by-external-id
  [db tenant-id external-task-id]
  (some-> (jdbc/execute-one!
           db
           [(str (select-task-sql)
                 " WHERE tenant_id = ? AND external_task_id = ?")
            tenant-id
            external-task-id])
          model/row->task))

(defn- unique-violation?
  [e]
  (and (instance? org.postgresql.util.PSQLException e)
       (= "23505" (.getSQLState ^org.postgresql.util.PSQLException e))))

(defn create-task!
  [db input]
  (let [validated (schema/validate-create-input! input)
        task-id (UUID/randomUUID)
        started-at (->timestamp (or (:started_at validated) (now)))]
    (try
      (let [row (jdbc/execute-one!
                 db
                 ["INSERT INTO ai_task (
                     id, tenant_id, external_task_id, workflow, task_type,
                     status, started_at, finished_at, metadata
                   ) VALUES (?, ?, ?, ?, ?, ?, ?, NULL, ?::jsonb)
                   RETURNING id, tenant_id, external_task_id, workflow, task_type,
                             status, started_at, finished_at, metadata, created_at, updated_at"
                  task-id
                  (:tenant_id validated)
                  (:external_task_id validated)
                  (:workflow validated)
                  (:task_type validated)
                  "started"
                  started-at
                  (encode-metadata (:metadata validated))])]
        (-> row model/row->task model/validate-task-invariants!))
      (catch org.postgresql.util.PSQLException e
        (if (unique-violation? e)
          (throw (errors/conflict-error
                  "Duplicate external_task_id for tenant"
                  {:details {:tenant_id (:tenant_id validated)
                              :external_task_id (:external_task_id validated)}}))
          (throw e))))))

(defn update-task-status!
  [db task-id status finished-at & {:keys [metadata]}]
  (schema/validate-status! status)
  (let [finished-at' (->timestamp (if (model/started? status) nil finished-at))
        row (jdbc/execute-one!
             db
             ["UPDATE ai_task
               SET status = ?, finished_at = ?, metadata = ?::jsonb, updated_at = now()
               WHERE id = ?
               RETURNING id, tenant_id, external_task_id, workflow, task_type,
                         status, started_at, finished_at, metadata, created_at, updated_at"
              (model/status->db status)
              finished-at'
              (encode-metadata (or metadata {}))
              task-id])]
    (when-not row
      (throw (errors/not-found-error "Task not found" {:details {:task-id task-id}})))
    (-> row model/row->task model/validate-task-invariants!)))

(defn finish-task!
  [db task-id finish-data]
  (let [validated (schema/validate-finish-input! finish-data)
        task (find-task-by-id db task-id)]
    (when (nil? task)
      (throw (errors/not-found-error "Task not found" {:details {:task-id task-id}})))
    (model/validate-finish-transition! task (:status validated))
    (let [finished-at (->timestamp (:finished_at validated))
          metadata (model/merge-metadata (:task/metadata task) (:metadata validated))
          row (jdbc/execute-one!
               db
               ["UPDATE ai_task
                 SET status = ?, finished_at = ?, metadata = ?::jsonb, updated_at = now()
                 WHERE id = ? AND status = 'started'
                 RETURNING id, tenant_id, external_task_id, workflow, task_type,
                           status, started_at, finished_at, metadata, created_at, updated_at"
                (model/status->db (:status validated))
                finished-at
                (encode-metadata metadata)
                task-id])]
      (when-not row
        (throw (errors/conflict-error
                "Task is not in started status"
                {:details {:task-id task-id :status (:task/status task)}})))
      (-> row model/row->task model/validate-task-invariants!))))
