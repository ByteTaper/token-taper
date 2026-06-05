;; SPDX-FileCopyrightText: 2026 Haluan Irsad
;; SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Commercial

(ns token-taper.task.test-support
  (:require
   [token-taper.db.jdbc :as jdbc])
  (:import
   [java.time Instant]
   [java.util UUID]))

(defn new-uuid
  []
  (UUID/randomUUID))

(defn insert-tenant!
  ([db] (insert-tenant! db {}))
  ([db {:keys [id name slug status]
        :or {id (new-uuid)
             name "Test Tenant"
             slug (str "tenant-" (new-uuid))
             status "active"}}]
  (jdbc/execute-one!
   db
   ["INSERT INTO tenant (id, name, slug, status, created_at, updated_at)
     VALUES (?, ?, ?, ?, now(), now())
     RETURNING id"
    id name slug status])
   id))

(defn delete-tenant!
  [db tenant-id]
  (jdbc/execute!
   db
   ["DELETE FROM tenant WHERE id = ?" tenant-id]))

(defn delete-task!
  [db task-id]
  (jdbc/execute!
   db
   ["DELETE FROM ai_task WHERE id = ?" task-id]))

(defn sample-create-input
  [tenant-id & {:keys [external-task-id workflow task-type metadata started-at]
                :or {metadata {}}}]
  (cond-> {:tenant_id tenant-id
           :metadata metadata}
    external-task-id (assoc :external_task_id external-task-id)
    workflow (assoc :workflow workflow)
    task-type (assoc :task_type task-type)
    started-at (assoc :started_at started-at)))

(defn sample-finish-input
  [& {:keys [status finished-at metadata]
      :or {status :finished
           finished-at (Instant/now)}}]
  (cond-> {:status status
           :finished_at finished-at}
    metadata (assoc :metadata metadata)))
