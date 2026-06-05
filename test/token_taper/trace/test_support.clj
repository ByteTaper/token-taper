;; SPDX-FileCopyrightText: 2026 Haluan Irsad
;; SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Commercial

(ns token-taper.trace.test-support
  (:require
   [token-taper.db.jdbc :as jdbc]
   [token-taper.task.repository :as task-repo]
   [token-taper.task.test-support :as task-support])
  (:import
   [java.time Instant]
   [java.util UUID]))

(defn sample-create-input
  [tenant-id task-id & {:keys [parent-span-id external-span-id span-type name metadata started-at]
                        :or {span-type :agent_step
                             metadata {}}}]
  (cond-> {:tenant_id tenant-id
           :task_id task-id
           :span_type span-type
           :metadata metadata}
    parent-span-id (assoc :parent_span_id parent-span-id)
    external-span-id (assoc :external_span_id external-span-id)
    name (assoc :name name)
    started-at (assoc :started_at started-at)))

(defn sample-finish-input
  [& {:keys [status finished-at metadata]
      :or {status :finished
           finished-at (Instant/now)}}]
  (cond-> {:status status
           :finished_at finished-at}
    metadata (assoc :metadata metadata)))

(defn insert-tenant-and-task!
  [db & task-opts]
  (let [tenant-id (task-support/insert-tenant! db)
        task (task-repo/create-task!
              db
              (apply task-support/sample-create-input tenant-id task-opts))]
    {:tenant-id tenant-id
     :task task}))

(defn delete-span!
  [db span-id]
  (jdbc/execute! db ["DELETE FROM ai_span WHERE id = ?" span-id]))

(defn span-count-for-task
  [db task-id]
  (:count
   (jdbc/execute-one!
    db
    ["SELECT COUNT(*)::int AS count FROM ai_span WHERE task_id = ?" task-id])))
