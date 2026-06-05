;; SPDX-FileCopyrightText: 2026 Haluan Irsad
;; SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Commercial

(ns token-taper.task.api-schema-test
  (:require
   [clojure.test :refer [deftest is]]
   [malli.core :as m]
   [token-taper.task.api-schema :as api-schema])
  (:import
   [java.time Instant]
   [java.util UUID]))

(deftest validate-start-request-normalizes-uuid-test
  (let [tenant-id (UUID/randomUUID)
        result (api-schema/validate-start-request!
                {:tenant_id (str tenant-id)})]
    (is (= tenant-id (:tenant_id result)))
    (is (= {} (:metadata result)))))

(deftest validate-start-request-normalizes-started-at-test
  (let [tenant-id (UUID/randomUUID)
        inst (Instant/parse "2026-06-05T10:00:00Z")
        result (api-schema/validate-start-request!
                {:tenant_id tenant-id
                 :started_at "2026-06-05T10:00:00Z"})]
    (is (= inst (:started_at result)))))

(deftest validate-start-request-rejects-invalid-tenant-test
  (is (thrown? clojure.lang.ExceptionInfo
               (api-schema/validate-start-request!
                {:tenant_id "not-a-uuid"}))))

(deftest validate-start-request-rejects-invalid-started-at-test
  (let [tenant-id (UUID/randomUUID)]
    (try
      (api-schema/validate-start-request!
       {:tenant_id tenant-id
        :started_at "not-a-time"})
      (is false "expected validation error")
      (catch clojure.lang.ExceptionInfo e
        (is (= :validation (:error/kind (ex-data e))))
        (is (= [{:field "started_at" :reason "invalid"}]
               (:error/details (ex-data e))))))))

(deftest task-start-response-shape-test
  (let [tenant-id (UUID/randomUUID)
        task-id (UUID/randomUUID)
        started (Instant/now)
        body (api-schema/start-task-response
              {:task/id task-id
               :tenant/id tenant-id
               :task/external-id "ext-1"
               :task/workflow "wf"
               :task/type "agentic_workflow"
               :task/status :started
               :task/started-at started
               :task/finished-at nil
               :task/metadata {:env "dev"}})]
    (is (= (str task-id) (:task_id body)))
    (is (= (str tenant-id) (:tenant_id body)))
    (is (= "started" (:status body)))
    (is (nil? (:finished_at body)))
    (is (= "dev" (get-in body [:metadata :env])))))

(deftest start-task-response-schema-rejects-invalid-status-test
  (is (false? (m/validate api-schema/StartTaskResponse
                          {:task_id (str (UUID/randomUUID))
                           :tenant_id (str (UUID/randomUUID))
                           :status "finished"
                           :started_at "2026-06-05T10:00:00Z"
                           :metadata {}}))))

(deftest validate-start-response-rejects-invalid-started-at-test
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Invalid task start response"
                        (api-schema/validate-start-response!
                         {:task_id (str (UUID/randomUUID))
                          :tenant_id (str (UUID/randomUUID))
                          :status "started"
                          :started_at "not-rfc3339"
                          :finished_at nil
                          :metadata {}}))))
