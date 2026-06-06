;; SPDX-FileCopyrightText: 2026 Haluan Irsad
;; SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Commercial

(ns token-taper.event.service
  (:require
   [token-taper.event.api-schema :as api-schema]
   [token-taper.event.repository :as repository]
   [token-taper.observability.logging :as logging]
   [token-taper.observability.metrics :as metrics]))

(defn- log-request-fields
  [request]
  (cond-> {}
    (:tenant_id request) (assoc :tenant_id (:tenant_id request))
    (:task_id request) (assoc :task_id (:task_id request))
    (:span_id request) (assoc :span_id (:span_id request))
    (:provider request) (assoc :provider (:provider request))
    (:model request) (assoc :model (:model request))
    (:status request) (assoc :status (:status request))))

(defn record-llm-call!
  [db request {:keys [logger metrics]}]
  (logging/info! logger :llm_call_event_ingest_requested (log-request-fields request))
  (try
    (let [validated (api-schema/validate-llm-call-request! request)
          create-input (api-schema/llm-call-request->create-input validated)
          event (repository/create-event! db create-input)]
      (when metrics
        (metrics/record-llm-call-event! metrics
                                        {:provider (:event/provider event)
                                         :status (name (:event/status event))}))
      (logging/info! logger :llm_call_event_recorded
                     {:event_id (:event/id event)
                      :tenant_id (:tenant/id event)
                      :task_id (:task/id event)
                      :span_id (:span/id event)
                      :provider (:event/provider event)
                      :model (:event/model event)
                      :input_tokens (:event/input-tokens event)
                      :output_tokens (:event/output-tokens event)
                      :latency_ms (:event/latency-ms event)
                      :status (:event/status event)})
      event)
    (catch clojure.lang.ExceptionInfo e
      (logging/error! logger :llm_call_event_ingest_failed
                      (merge (log-request-fields request)
                             {:error_kind (:error/kind (ex-data e))}
                             (logging/build-error-fields logger e)))
      (throw e))))
