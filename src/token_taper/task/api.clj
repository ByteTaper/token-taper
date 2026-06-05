;; SPDX-FileCopyrightText: 2026 Haluan Irsad
;; SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Commercial

(ns token-taper.task.api
  (:require
   [token-taper.api.response :as response]
   [token-taper.task.api-schema :as api-schema]
   [token-taper.task.errors :as errors]
   [token-taper.task.service :as service]))

(defn start-task-handler
  [{:keys [datasource logger metrics]}]
  (fn [request]
    (let [body (or (:json-body request) {})]
      (try
        (let [task (service/start-task! datasource body {:logger logger
                                                         :metrics metrics})]
          (response/created-flat (api-schema/start-task-response task)))
        (catch clojure.lang.ExceptionInfo e
          (cond
            (errors/validation-error? e)
            (response/validation-error-response
             (or (:error/message (ex-data e)) "Invalid task start request.")
             (api-schema/validation-details->api (:error/details (ex-data e))))

            (errors/conflict-error? e)
            (response/conflict-error-response
             "Task with the same external_task_id already exists for this tenant."
             (api-schema/conflict-details->api (:error/details (ex-data e))))

            :else (throw e)))))))
