;; SPDX-FileCopyrightText: 2026 Haluan Irsad
;; SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Commercial

(ns token-taper.trace.service
  (:require
   [token-taper.event.repository :as event-repository]
   [token-taper.observability.logging :as logging]
   [token-taper.observability.metrics :as metrics]
   [token-taper.task.api-schema :as api-schema]
   [token-taper.task.errors :as errors]
   [token-taper.task.repository :as task-repository]
   [token-taper.trace.span-repository :as span-repository]))

(defn get-task-trace!
  [db task-id-str {:keys [logger metrics]}]
  (let [task-id (api-schema/parse-task-path-id! task-id-str)]
    (logging/info! logger :task_trace_requested {:task_id task-id})
    (try
      (if-let [task (task-repository/find-task-by-id db task-id)]
        (let [spans (span-repository/find-spans-by-task-id db task-id)
              events (event-repository/find-events-by-task-id db task-id)]
          (when metrics
            (metrics/record-task-trace! metrics))
          (logging/info! logger :task_trace_returned
                         {:task_id task-id
                          :span_count (count spans)
                          :event_count (count events)})
          {:task task :spans spans :events events})
        (throw (errors/not-found-error
                "Task not found"
                {:details {:task-id task-id}})))
      (catch clojure.lang.ExceptionInfo e
        (logging/error! logger :task_trace_failed
                        (merge {:task_id task-id}
                               {:error_kind (:error/kind (ex-data e))}
                               (logging/build-error-fields logger e)))
        (throw e)))))
