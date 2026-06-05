;; SPDX-FileCopyrightText: 2026 Haluan Irsad
;; SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Commercial

(ns token-taper.task.service
  (:require
   [token-taper.observability.logging :as logging]
   [token-taper.observability.metrics :as metrics]
   [token-taper.task.api-schema :as api-schema]
   [token-taper.task.errors :as errors]
   [token-taper.task.repository :as repository])
  (:import
   [java.time Instant]))

(defn- log-fields
  [request]
  (cond-> {}
    (:tenant_id request) (assoc :tenant_id (:tenant_id request))
    (:external_task_id request) (assoc :external_task_id (:external_task_id request))
    (:workflow request) (assoc :workflow (:workflow request))))

(defn start-task!
  [db request {:keys [logger metrics]}]
  (logging/info! logger :task_start_requested (log-fields request))
  (try
    (let [validated (api-schema/validate-start-request! request)
          task (repository/create-task! db validated)]
      (when metrics
        (metrics/record-task-started! metrics))
      (logging/info! logger :task_started
                     {:task_id (:task/id task)
                      :tenant_id (:tenant/id task)
                      :external_task_id (:task/external-id task)
                      :workflow (:task/workflow task)})
      task)
    (catch clojure.lang.ExceptionInfo e
      (logging/error! logger :task_start_failed
                      (merge (log-fields request)
                             {:error_kind (:error/kind (ex-data e))}
                             (logging/build-error-fields logger e)))
      (throw e))))

(defn finish-task!
  [db task-id-str request {:keys [logger metrics]}]
  (let [task-id (api-schema/parse-task-id! task-id-str)]
    (logging/info! logger :task_finish_requested
                   (cond-> {:task_id task-id}
                     (:status request) (assoc :status (:status request))))
    (try
      (let [validated (api-schema/validate-finish-request! request)
            finish-data (assoc validated
                               :finished_at (or (:finished_at validated) (Instant/now)))
            task (repository/finish-task! db task-id finish-data)]
        (when metrics
          (metrics/record-task-finished! metrics
                                         {:status (name (:task/status task))
                                          :workflow (or (:task/workflow task) "unknown")}))
        (logging/info! logger :task_finished
                       {:task_id (:task/id task)
                        :tenant_id (:tenant/id task)
                        :external_task_id (:task/external-id task)
                        :workflow (:task/workflow task)
                        :status (:task/status task)})
        task)
      (catch clojure.lang.ExceptionInfo e
        (logging/error! logger :task_finish_failed
                        (merge {:task_id task-id}
                               {:error_kind (:error/kind (ex-data e))}
                               (logging/build-error-fields logger e)))
        (throw e)))))

(defn get-task!
  [db task-id-str {:keys [logger metrics]}]
  (let [task-id (api-schema/parse-task-path-id! task-id-str)]
    (logging/info! logger :task_detail_requested {:task_id task-id})
    (try
      (if-let [task (repository/find-task-by-id db task-id)]
        (do
          (when metrics
            (metrics/record-task-detail! metrics))
          (logging/info! logger :task_detail_returned
                         {:task_id (:task/id task)
                          :tenant_id (:tenant/id task)
                          :status (:task/status task)
                          :workflow (:task/workflow task)})
          task)
        (throw (errors/not-found-error
                "Task not found"
                {:details {:task-id task-id}})))
      (catch clojure.lang.ExceptionInfo e
        (logging/error! logger :task_detail_failed
                        (merge {:task_id task-id}
                               {:error_kind (:error/kind (ex-data e))}
                               (logging/build-error-fields logger e)))
        (throw e)))))
