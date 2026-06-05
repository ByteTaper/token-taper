;; SPDX-FileCopyrightText: 2026 Haluan Irsad
;; SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Commercial

(ns token-taper.task.api
  (:require
   [token-taper.api.response :as response]
   [token-taper.task.api-schema :as api-schema]
   [token-taper.task.errors :as errors]
   [token-taper.task.service :as service]
   [token-taper.trace.service :as trace-service]))

(defn- handle-task-api-exception
  [e {:keys [validation-fallback conflict-message conflict-details-fn not-found?]}]
  (cond
    (errors/validation-error? e)
    (response/validation-error-response
     (or (:error/message (ex-data e)) validation-fallback)
     (api-schema/validation-details->api (:error/details (ex-data e))))

    (and not-found? (errors/not-found-error? e))
    (response/not-found-error-response (api-schema/not-found-details->api nil))

    (errors/conflict-error? e)
    (response/conflict-error-response
     conflict-message
     (if conflict-details-fn
       (conflict-details-fn (:error/details (ex-data e)))
       (api-schema/conflict-details->api (:error/details (ex-data e)))))

    :else (throw e)))

(defn start-task-handler
  [{:keys [datasource logger metrics]}]
  (fn [request]
    (let [body (or (:json-body request) {})]
      (try
        (let [task (service/start-task! datasource body {:logger logger
                                                         :metrics metrics})]
          (response/created-flat (api-schema/start-task-response task)))
        (catch clojure.lang.ExceptionInfo e
          (handle-task-api-exception
           e {:validation-fallback "Invalid task start request."
              :conflict-message "Task with the same external_task_id already exists for this tenant."
              :conflict-details-fn api-schema/conflict-details->api}))))))

(defn finish-task-handler
  [{:keys [datasource logger metrics]}]
  (fn [request]
    (let [task-id (get-in request [:path-params :task_id])
          body (or (:json-body request) {})]
      (try
        (let [task (service/finish-task! datasource task-id body {:logger logger
                                                                  :metrics metrics})]
          (response/ok-flat (api-schema/finish-task-response task)))
        (catch clojure.lang.ExceptionInfo e
          (handle-task-api-exception
           e {:validation-fallback "Invalid task finish request."
              :not-found? true
              :conflict-message "Task is already in a terminal status."
              :conflict-details-fn api-schema/finish-conflict-details->api}))))))

(defn get-task-handler
  [{:keys [datasource logger metrics]}]
  (fn [request]
    (let [task-id (get-in request [:path-params :task_id])]
      (try
        (let [task (service/get-task! datasource task-id {:logger logger
                                                          :metrics metrics})]
          (response/ok-flat (api-schema/task-detail-response task)))
        (catch clojure.lang.ExceptionInfo e
          (handle-task-api-exception
           e {:validation-fallback "Invalid task_id path parameter."
              :not-found? true}))))))

(defn get-task-trace-handler
  [{:keys [datasource logger metrics]}]
  (fn [request]
    (let [task-id (get-in request [:path-params :task_id])]
      (try
        (let [trace (trace-service/get-task-trace! datasource task-id {:logger logger
                                                                       :metrics metrics})]
          (response/ok-flat (api-schema/task-trace-response trace)))
        (catch clojure.lang.ExceptionInfo e
          (handle-task-api-exception
           e {:validation-fallback "Invalid task_id path parameter."
              :not-found? true}))))))
