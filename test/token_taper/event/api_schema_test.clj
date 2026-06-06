;; SPDX-FileCopyrightText: 2026 Haluan Irsad
;; SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Commercial

(ns token-taper.event.api-schema-test
  (:require
   [clojure.test :refer [deftest is]]
   [malli.core :as m]
   [token-taper.event.api-schema :as api-schema]
   [token-taper.task.errors :as errors])
  (:import
   [java.time Instant]
   [java.util UUID]))

(defn- sample-wire-request
  [tenant-id task-id]
  {:tenant_id (str tenant-id)
   :task_id (str task-id)
   :provider "anthropic"
   :model "claude-sonnet"
   :input_tokens 3000
   :output_tokens 700
   :latency_ms 3400
   :status "success"})

(deftest validate-llm-call-normalizes-uuids-test
  (let [tenant-id (UUID/randomUUID)
        task-id (UUID/randomUUID)
        result (api-schema/validate-llm-call-request!
                (sample-wire-request tenant-id task-id))]
    (is (= tenant-id (:tenant_id result)))
    (is (= task-id (:task_id result)))
    (is (= :success (:status result)))
    (is (= {} (:metadata result)))))

(deftest validate-llm-call-rejects-missing-provider-test
  (let [tenant-id (UUID/randomUUID)
        task-id (UUID/randomUUID)]
    (is (thrown? clojure.lang.ExceptionInfo
                 (api-schema/validate-llm-call-request!
                  (dissoc (sample-wire-request tenant-id task-id) :provider))))))

(deftest validate-llm-call-rejects-negative-input-tokens-test
  (let [tenant-id (UUID/randomUUID)
        task-id (UUID/randomUUID)]
    (is (thrown? clojure.lang.ExceptionInfo
                 (api-schema/validate-llm-call-request!
                  (assoc (sample-wire-request tenant-id task-id)
                         :input_tokens -1))))))

(deftest validate-llm-call-rejects-invalid-status-test
  (let [tenant-id (UUID/randomUUID)
        task-id (UUID/randomUUID)]
    (is (thrown? clojure.lang.ExceptionInfo
                 (api-schema/validate-llm-call-request!
                  (assoc (sample-wire-request tenant-id task-id)
                         :status "running"))))))

(deftest llm-call-request->create-input-defaults-occurred-at-test
  (let [tenant-id (UUID/randomUUID)
        task-id (UUID/randomUUID)
        validated (api-schema/validate-llm-call-request!
                   (sample-wire-request tenant-id task-id))
        create-input (api-schema/llm-call-request->create-input validated)]
    (is (= :llm_call (:event_type create-input)))
    (is (instance? Instant (:occurred_at create-input)))))

(deftest llm-call-event-response-shape-test
  (let [tenant-id (UUID/randomUUID)
        task-id (UUID/randomUUID)
        event-id (UUID/randomUUID)
        span-id (UUID/randomUUID)
        occurred (Instant/parse "2026-06-05T10:00:03Z")
        body (api-schema/llm-call-event-response
              {:event/id event-id
               :tenant/id tenant-id
               :task/id task-id
               :span/id span-id
               :event/external-id "evt-1"
               :event/type :llm_call
               :event/provider "anthropic"
               :event/model "claude-sonnet"
               :event/input-tokens 3000
               :event/output-tokens 700
               :event/cached-tokens 0
               :event/latency-ms 3400
               :event/status :success
               :event/occurred-at occurred
               :event/metadata {:temperature 0.2}})]
    (is (= (str event-id) (:event_id body)))
    (is (= "llm_call" (:event_type body)))
    (is (= "2026-06-05T10:00:03Z" (:occurred_at body)))))

(deftest llm-call-response-schema-rejects-wrong-event-type-test
  (is (false? (m/validate api-schema/LlmCallEventResponse
                          {:event_id (str (UUID/randomUUID))
                           :tenant_id (str (UUID/randomUUID))
                           :task_id (str (UUID/randomUUID))
                           :event_type "tool_call"
                           :provider "x"
                           :model "y"
                           :input_tokens 1
                           :output_tokens 1
                           :latency_ms 1
                           :status "success"
                           :occurred_at "2026-06-05T10:00:03Z"
                           :metadata {}}))))

(deftest error-kind-test
  (try
    (api-schema/validate-llm-call-request! {})
    (is false)
    (catch clojure.lang.ExceptionInfo e
      (is (errors/validation-error? e)))))
