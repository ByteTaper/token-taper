;; SPDX-FileCopyrightText: 2026 Haluan Irsad
;; SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Commercial

(ns token-taper.trace.span-schema-test
  (:require
   [clojure.test :refer [deftest is]]
   [token-taper.task.errors :as errors]
   [token-taper.trace.span-schema :as schema])
  (:import
   [java.time Instant]
   [java.util UUID]))

(deftest validate-create-input-accepts-valid-data-test
  (let [tenant-id (UUID/randomUUID)
        task-id (UUID/randomUUID)
        result (schema/validate-create-input!
                {:tenant_id tenant-id
                 :task_id task-id
                 :span_type :agent_step
                 :external_span_id "span_01"
                 :name "classify"})]
    (is (= tenant-id (:tenant_id result)))
    (is (= {} (:metadata result)))))

(deftest validate-create-input-defaults-metadata-test
  (is (= {} (:metadata (schema/validate-create-input!
                       {:tenant_id (UUID/randomUUID)
                        :task_id (UUID/randomUUID)
                        :span_type :workflow})))))

(deftest validate-create-input-rejects-missing-tenant-test
  (is (thrown? clojure.lang.ExceptionInfo
               (schema/validate-create-input!
                {:task_id (UUID/randomUUID)
                 :span_type :workflow}))))

(deftest validate-create-input-rejects-invalid-span-type-test
  (is (thrown? clojure.lang.ExceptionInfo
               (schema/validate-create-input!
                {:tenant_id (UUID/randomUUID)
                 :task_id (UUID/randomUUID)
                 :span_type :invalid}))))

(deftest validate-finish-input-accepts-terminal-status-test
  (is (= :finished
         (:status (schema/validate-finish-input!
                   {:status :finished
                    :finished_at (Instant/now)})))))

(deftest validate-finish-input-rejects-started-status-test
  (is (thrown? clojure.lang.ExceptionInfo
               (schema/validate-finish-input!
                {:status :started
                 :finished_at (Instant/now)}))))

(deftest error-kind-test
  (try
    (schema/validate-create-input! {})
    (is false)
    (catch clojure.lang.ExceptionInfo e
      (is (errors/validation-error? e)))))

(deftest validate-span-type-rejects-invalid-test
  (is (thrown? clojure.lang.ExceptionInfo
               (schema/validate-span-type! :bogus))))
