;; SPDX-FileCopyrightText: 2026 Haluan Irsad
;; SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Commercial

(ns token-taper.task.service
  (:require
   [token-taper.observability.logging :as logging]
   [token-taper.observability.metrics :as metrics]
   [token-taper.task.api-schema :as api-schema]
   [token-taper.task.errors :as errors]
   [token-taper.task.repository :as repository]))

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
