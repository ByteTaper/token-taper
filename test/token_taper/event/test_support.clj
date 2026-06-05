;; SPDX-FileCopyrightText: 2026 Haluan Irsad
;; SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Commercial

(ns token-taper.event.test-support
  (:require
   [token-taper.db.jdbc :as jdbc]
   [token-taper.trace.span-repository :as span-repo]
   [token-taper.trace.test-support :as span-support])
  (:import
   [java.time Instant]
   [java.util UUID]))

(defn- base-input
  [tenant-id task-id occurred-at]
  {:tenant_id tenant-id
   :task_id task-id
   :status :success
   :occurred_at occurred-at})

(defn sample-llm-call-input
  [tenant-id task-id & {:keys [span-id external-event-id occurred-at metadata]
                        :or {occurred-at (Instant/now)
                             metadata {}}}]
  (cond-> (merge (base-input tenant-id task-id occurred-at)
                 {:event_type :llm_call
                  :provider "anthropic"
                  :model "claude-sonnet"
                  :input_tokens 3000
                  :output_tokens 700
                  :latency_ms 3400
                  :metadata metadata})
    span-id (assoc :span_id span-id)
    external-event-id (assoc :external_event_id external-event-id)))

(defn sample-tool-call-input
  [tenant-id task-id & {:keys [span-id external-event-id occurred-at metadata]
                        :or {occurred-at (Instant/now)
                             metadata {}}}]
  (cond-> (merge (base-input tenant-id task-id occurred-at)
                 {:event_type :tool_call
                  :tool_name "vector_search"
                  :latency_ms 120
                  :metadata metadata})
    span-id (assoc :span_id span-id)
    external-event-id (assoc :external_event_id external-event-id)))

(defn sample-retry-input
  [tenant-id task-id & {:keys [span-id external-event-id occurred-at metadata]
                        :or {occurred-at (Instant/now)}}]
  (cond-> (merge (base-input tenant-id task-id occurred-at)
                 {:event_type :retry
                  :retry_count 1
                  :metadata (merge {:reason "invalid_json"} metadata)})
    span-id (assoc :span_id span-id)
    external-event-id (assoc :external_event_id external-event-id)))

(defn sample-cache-input
  [tenant-id task-id & {:keys [span-id external-event-id occurred-at cache-hit metadata]
                        :or {occurred-at (Instant/now)
                             cache-hit false
                             metadata {}}}]
  (cond-> (merge (base-input tenant-id task-id occurred-at)
                 {:event_type :cache
                  :cache_hit cache-hit
                  :metadata metadata})
    span-id (assoc :span_id span-id)
    external-event-id (assoc :external_event_id external-event-id)))

(defn insert-tenant-task-span!
  [db & {:keys [span-name span-type]
         :or {span-name "test-span"
              span-type :agent_step}}]
  (let [{:keys [tenant-id task]} (span-support/insert-tenant-and-task! db)
        task-id (:task/id task)
        span (span-repo/create-span!
              db
              (span-support/sample-create-input tenant-id task-id
                                                :name span-name
                                                :span-type span-type))]
    {:tenant-id tenant-id
     :task task
     :task-id task-id
     :span span}))

(defn event-count-for-task
  [db task-id]
  (:count
   (jdbc/execute-one!
    db
    ["SELECT COUNT(*)::int AS count FROM ai_event WHERE task_id = ?" task-id])))

(defn event-span-id
  [db event-id]
  (:span-id
   (jdbc/execute-one!
    db
    ["SELECT span_id FROM ai_event WHERE id = ?" event-id])))

(defn delete-event!
  [db event-id]
  (jdbc/execute! db ["DELETE FROM ai_event WHERE id = ?" event-id]))
