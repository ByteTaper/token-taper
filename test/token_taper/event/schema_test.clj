;; SPDX-FileCopyrightText: 2026 Haluan Irsad
;; SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Commercial

(ns token-taper.event.schema-test
  (:require
   [clojure.test :refer [deftest is]]
   [token-taper.event.schema :as schema]
   [token-taper.event.test-support :as support]
   [token-taper.task.errors :as errors])
  (:import
   [java.time Instant]
   [java.util UUID]))

(deftest validate-create-defaults-metadata-test
  (let [tenant-id (UUID/randomUUID)
        task-id (UUID/randomUUID)
        result (schema/validate-create-input!
                (support/sample-llm-call-input tenant-id task-id))]
    (is (= {} (:metadata result)))))

(deftest validate-create-rejects-missing-tenant-test
  (is (thrown? clojure.lang.ExceptionInfo
               (schema/validate-create-input!
                {:task_id (UUID/randomUUID)
                 :event_type :llm_call
                 :status :success
                 :occurred_at (Instant/now)}))))

(deftest validate-create-rejects-invalid-event-type-test
  (is (thrown? clojure.lang.ExceptionInfo
               (schema/validate-create-input!
                {:tenant_id (UUID/randomUUID)
                 :task_id (UUID/randomUUID)
                 :event_type :invalid
                 :status :success
                 :occurred_at (Instant/now)}))))

(deftest validate-create-rejects-invalid-status-test
  (let [tenant-id (UUID/randomUUID)
        task-id (UUID/randomUUID)]
    (is (thrown? clojure.lang.ExceptionInfo
                 (schema/validate-create-input!
                  (assoc (support/sample-llm-call-input tenant-id task-id)
                         :status :started))))))

(deftest validate-create-rejects-negative-input-tokens-test
  (let [tenant-id (UUID/randomUUID)
        task-id (UUID/randomUUID)]
    (is (thrown? clojure.lang.ExceptionInfo
                 (schema/validate-create-input!
                  (assoc (support/sample-llm-call-input tenant-id task-id)
                         :input_tokens -1))))))

(deftest validate-create-rejects-negative-output-tokens-test
  (let [tenant-id (UUID/randomUUID)
        task-id (UUID/randomUUID)]
    (is (thrown? clojure.lang.ExceptionInfo
                 (schema/validate-create-input!
                  (assoc (support/sample-llm-call-input tenant-id task-id)
                         :output_tokens -1))))))

(deftest validate-create-rejects-negative-cached-tokens-test
  (let [tenant-id (UUID/randomUUID)
        task-id (UUID/randomUUID)]
    (is (thrown? clojure.lang.ExceptionInfo
                 (schema/validate-create-input!
                  (assoc (support/sample-llm-call-input tenant-id task-id)
                         :cached_tokens -1))))))

(deftest validate-create-rejects-negative-latency-ms-test
  (let [tenant-id (UUID/randomUUID)
        task-id (UUID/randomUUID)]
    (is (thrown? clojure.lang.ExceptionInfo
                 (schema/validate-create-input!
                  (assoc (support/sample-llm-call-input tenant-id task-id)
                         :latency_ms -1))))))

(deftest validate-create-rejects-negative-retry-count-test
  (let [tenant-id (UUID/randomUUID)
        task-id (UUID/randomUUID)]
    (is (thrown? clojure.lang.ExceptionInfo
                 (schema/validate-create-input!
                  (assoc (support/sample-retry-input tenant-id task-id)
                         :retry_count -1))))))

(deftest validate-status-rejects-invalid-test
  (is (thrown? clojure.lang.ExceptionInfo
               (schema/validate-status! :started))))

(deftest validate-llm-call-shape-test
  (let [tenant-id (UUID/randomUUID)
        task-id (UUID/randomUUID)]
    (is (= :llm_call
           (:event_type (schema/validate-create-input!
                         (support/sample-llm-call-input tenant-id task-id)))))))

(deftest validate-llm-call-rejects-incomplete-success-test
  (let [tenant-id (UUID/randomUUID)
        task-id (UUID/randomUUID)]
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Successful LLM call"
                          (schema/validate-create-input!
                           {:tenant_id tenant-id
                            :task_id task-id
                            :event_type :llm_call
                            :status :success
                            :provider "anthropic"
                            :model "claude"
                            :occurred_at (Instant/now)})))))

(deftest validate-llm-failure-without-tokens-test
  (let [tenant-id (UUID/randomUUID)
        task-id (UUID/randomUUID)
        result (schema/validate-create-input!
                {:tenant_id tenant-id
                 :task_id task-id
                 :event_type :llm_call
                 :status :timeout
                 :provider "anthropic"
                 :model "claude"
                 :error_code "timeout"
                 :occurred_at (Instant/now)})]
    (is (= :timeout (:status result)))
    (is (nil? (:input_tokens result)))))

(deftest validate-create-coerces-occurred-at-string-test
  (let [tenant-id (UUID/randomUUID)
        task-id (UUID/randomUUID)
        inst (Instant/parse "2026-06-05T10:00:00Z")
        result (schema/validate-create-input!
                (assoc (support/sample-llm-call-input tenant-id task-id)
                       :occurred_at "2026-06-05T10:00:00Z"))]
    (is (= inst (:occurred_at result)))))

(deftest validate-create-rejects-missing-occurred-at-test
  (let [tenant-id (UUID/randomUUID)
        task-id (UUID/randomUUID)]
    (is (thrown? clojure.lang.ExceptionInfo
                 (schema/validate-create-input!
                  (dissoc (support/sample-llm-call-input tenant-id task-id)
                          :occurred_at))))))

(deftest validate-tool-call-shape-test
  (let [tenant-id (UUID/randomUUID)
        task-id (UUID/randomUUID)]
    (is (= :tool_call
           (:event_type (schema/validate-create-input!
                         (support/sample-tool-call-input tenant-id task-id)))))))

(deftest validate-retry-shape-test
  (let [tenant-id (UUID/randomUUID)
        task-id (UUID/randomUUID)]
    (is (= :retry
           (:event_type (schema/validate-create-input!
                         (support/sample-retry-input tenant-id task-id)))))))

(deftest validate-retry-rejects-missing-reason-test
  (let [tenant-id (UUID/randomUUID)
        task-id (UUID/randomUUID)]
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Retry event"
                          (schema/validate-create-input!
                           {:tenant_id tenant-id
                            :task_id task-id
                            :event_type :retry
                            :status :success
                            :retry_count 1
                            :metadata {}
                            :occurred_at (Instant/now)})))))

(deftest validate-cache-shape-test
  (let [tenant-id (UUID/randomUUID)
        task-id (UUID/randomUUID)]
    (is (= :cache
           (:event_type (schema/validate-create-input!
                         (support/sample-cache-input tenant-id task-id)))))))

(deftest error-kind-test
  (try
    (schema/validate-create-input! {})
    (is false)
    (catch clojure.lang.ExceptionInfo e
      (is (errors/validation-error? e)))))
