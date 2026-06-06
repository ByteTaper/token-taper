;; SPDX-FileCopyrightText: 2026 Haluan Irsad
;; SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Commercial

(ns token-taper.event.api
  (:require
   [token-taper.api.response :as response]
   [token-taper.event.api-schema :as api-schema]
   [token-taper.event.service :as service]
   [token-taper.task.errors :as errors]))

(defn- details-map
  [e]
  (:error/details (ex-data e)))

(defn- span-not-found?
  [details]
  (and (map? details) (contains? details :span_id)))

(defn- handle-event-api-exception
  [e {:keys [validation-fallback]}]
  (let [details (details-map e)]
    (cond
      (errors/validation-error? e)
      (response/validation-error-response
       (or (:error/message (ex-data e)) validation-fallback)
       (api-schema/validation-details->api details))

      (and (errors/not-found-error? e) (span-not-found? details))
      (response/span-not-found-error-response
       (api-schema/span-not-found-details->api details))

      (errors/not-found-error? e)
      (response/not-found-error-response
       (api-schema/task-not-found-details->api details))

      (errors/conflict-error? e)
      (response/event-conflict-error-response
       "Event with the same external_event_id already exists for this tenant."
       (api-schema/event-conflict-details->api details))

      :else (throw e))))

(defn ingest-llm-call-event-handler
  [{:keys [datasource logger metrics]}]
  (fn [request]
    (let [body (or (:json-body request) {})]
      (try
        (let [event (service/record-llm-call! datasource body {:logger logger
                                                               :metrics metrics})]
          (response/created-flat (api-schema/llm-call-event-response event)))
        (catch clojure.lang.ExceptionInfo e
          (handle-event-api-exception
           e {:validation-fallback "Invalid LLM call event request."}))))))
