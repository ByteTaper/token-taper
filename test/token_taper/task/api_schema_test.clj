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

(deftest validate-finish-request-normalizes-metadata-test
  (let [result (api-schema/validate-finish-request! {:status :finished})]
    (is (= {} (:metadata result)))))

(deftest validate-finish-request-normalizes-finished-at-test
  (let [inst (Instant/parse "2026-06-05T10:10:00Z")
        result (api-schema/validate-finish-request!
                {:status :finished
                 :finished_at "2026-06-05T10:10:00Z"})]
    (is (= inst (:finished_at result)))))

(deftest validate-finish-request-rejects-invalid-status-test
  (is (thrown? clojure.lang.ExceptionInfo
               (api-schema/validate-finish-request! {:status :started}))))

(deftest validate-finish-request-rejects-invalid-finished-at-test
  (try
    (api-schema/validate-finish-request!
     {:status :finished
      :finished_at "not-a-time"})
    (is false "expected validation error")
    (catch clojure.lang.ExceptionInfo e
      (is (= :validation (:error/kind (ex-data e))))
      (is (= [{:field "finished_at" :reason "invalid"}]
             (:error/details (ex-data e)))))))

(deftest parse-task-id-accepts-uuid-string-test
  (let [id (UUID/randomUUID)]
    (is (= id (api-schema/parse-task-id! (str id))))))

(deftest parse-task-id-rejects-invalid-test
  (try
    (api-schema/parse-task-id! "not-a-uuid")
    (is false "expected validation error")
    (catch clojure.lang.ExceptionInfo e
      (is (= :validation (:error/kind (ex-data e))))
      (is (= [{:field "task_id" :reason "invalid"}]
             (:error/details (ex-data e)))))))

(deftest finish-task-response-shape-test
  (let [tenant-id (UUID/randomUUID)
        task-id (UUID/randomUUID)
        started (Instant/parse "2026-06-05T10:00:00Z")
        finished (Instant/parse "2026-06-05T10:10:00Z")
        body (api-schema/finish-task-response
              {:task/id task-id
               :tenant/id tenant-id
               :task/external-id "ext-1"
               :task/workflow "wf"
               :task/type "agentic_workflow"
               :task/status :finished
               :task/started-at started
               :task/finished-at finished
               :task/metadata {:result "ok"}})]
    (is (= (str task-id) (:task_id body)))
    (is (= "finished" (:status body)))
    (is (= "2026-06-05T10:10:00Z" (:finished_at body)))
    (is (= "ok" (get-in body [:metadata :result])))))

(deftest finish-task-response-schema-rejects-missing-finished-at-test
  (is (false? (m/validate api-schema/FinishTaskResponse
                          {:task_id (str (UUID/randomUUID))
                           :tenant_id (str (UUID/randomUUID))
                           :status "finished"
                           :started_at "2026-06-05T10:00:00Z"
                           :metadata {}}))))

(deftest parse-task-path-id-accepts-uuid-string-test
  (let [id (UUID/randomUUID)]
    (is (= id (api-schema/parse-task-path-id! (str id))))))

(deftest parse-task-path-id-rejects-invalid-test
  (try
    (api-schema/parse-task-path-id! "not-a-uuid")
    (is false "expected validation error")
    (catch clojure.lang.ExceptionInfo e
      (is (= :validation (:error/kind (ex-data e))))
      (is (= [{:field "task_id" :reason "invalid_uuid"}]
             (:error/details (ex-data e)))))))

(deftest task-detail-response-includes-audit-timestamps-test
  (let [tenant-id (UUID/randomUUID)
        task-id (UUID/randomUUID)
        started (Instant/parse "2026-06-05T10:00:00Z")
        created (Instant/parse "2026-06-05T10:00:01Z")
        updated (Instant/parse "2026-06-05T10:00:12Z")
        body (api-schema/task-detail-response
              {:task/id task-id
               :tenant/id tenant-id
               :task/status :started
               :task/started-at started
               :task/finished-at nil
               :task/metadata {}
               :task/created-at created
               :task/updated-at updated})]
    (is (= "started" (:status body)))
    (is (= "2026-06-05T10:00:01Z" (:created_at body)))
    (is (= "2026-06-05T10:00:12Z" (:updated_at body)))))

(deftest task-trace-response-shape-test
  (let [tenant-id (UUID/randomUUID)
        task-id (UUID/randomUUID)
        span-id (UUID/randomUUID)
        event-id (UUID/randomUUID)
        started (Instant/parse "2026-06-05T10:00:00Z")
        occurred (Instant/parse "2026-06-05T10:00:04Z")
        body (api-schema/task-trace-response
              {:task {:task/id task-id
                      :tenant/id tenant-id
                      :task/status :started
                      :task/started-at started
                      :task/finished-at nil
                      :task/metadata {:environment "dev"}}
               :spans [{:span/id span-id
                        :task/id task-id
                        :span/parent-id nil
                        :span/type :workflow
                        :span/name "root"
                        :span/status :started
                        :span/started-at started
                        :span/finished-at nil
                        :span/metadata {}}]
               :events [{:event/id event-id
                         :task/id task-id
                         :span/id span-id
                         :event/type :llm_call
                         :event/status :success
                         :event/provider "anthropic"
                         :event/model "claude"
                         :event/input-tokens 100
                         :event/output-tokens 50
                         :event/latency-ms 1000
                         :event/metadata {}
                         :event/occurred-at occurred
                         :event/created-at occurred}]})]
    (is (= (str task-id) (get-in body [:task :task_id])))
    (is (= 1 (count (:spans body))))
    (is (= (str span-id) (:span_id (first (:spans body)))))
    (is (= 1 (count (:events body))))
    (is (= (str event-id) (:event_id (first (:events body)))))))
