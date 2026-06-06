;; SPDX-FileCopyrightText: 2026 Haluan Irsad
;; SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Commercial

(ns token-taper.api.llm-call-event-routes-test
  (:require
   [clojure.test :refer [deftest is]]
   [jsonista.core :as json]
   [ring.mock.request :as mock]
   [token-taper.api.server :as server]
   [token-taper.event.service :as event-service]
   [token-taper.task.errors :as errors]
   [token-taper.test-support.handler-fixtures :as fixtures]
   [token-taper.test-support.logging :as log-support])
  (:import
   [java.time Instant]
   [java.util UUID]))

(def ^:private mapper (json/object-mapper {:decode-key-fn keyword}))

(def ^:private health-system (fixtures/default-health-system (Object.)))

(defn- handler-opts []
  (let [logger (log-support/test-logger)]
    {:system-info (fixtures/system-info-payload (:app health-system) health-system)
     :health-system (assoc health-system :logger logger)
     :datasource (Object.)
     :logger logger}))

(defn- app []
  (server/handler (handler-opts)))

(defn- json-body [response]
  (json/read-value (:body response) mapper))

(defn- post-llm-call [body]
  ((app) (-> (mock/request :post "/v1/events/llm-call")
             (mock/content-type "application/json")
             (mock/json-body body))))

(defn- assert-validation-400
  [body & {:keys [field]}]
  (let [response (post-llm-call body)
        parsed (json-body response)]
    (is (= 400 (:status response)))
    (is (= "validation_error" (:error parsed)))
    (when field
      (is (= field (:field (first (:details parsed))))))))

(defn- sample-wire-body [tenant-id task-id]
  {:tenant_id (str tenant-id)
   :task_id (str task-id)
   :provider "anthropic"
   :model "claude-sonnet"
   :input_tokens 3000
   :output_tokens 700
   :latency_ms 3400
   :status "success"})

(defn- sample-event [tenant-id task-id]
  (let [event-id (UUID/randomUUID)
        occurred (Instant/parse "2026-06-05T10:00:03Z")]
    {:event/id event-id
     :tenant/id tenant-id
     :task/id task-id
     :span/id nil
     :event/external-id "evt-1"
     :event/type :llm_call
     :event/provider "anthropic"
     :event/model "claude-sonnet"
     :event/input-tokens 3000
     :event/output-tokens 700
     :event/latency-ms 3400
     :event/status :success
     :event/occurred-at occurred
     :event/metadata {}}))

(deftest ingest-llm-call-201-test
  (let [tenant-id (UUID/randomUUID)
        task-id (UUID/randomUUID)
        event (sample-event tenant-id task-id)]
    (with-redefs [event-service/record-llm-call! (fn [_ _ _] event)]
      (let [response (post-llm-call (sample-wire-body tenant-id task-id))
            body (json-body response)]
        (is (= 201 (:status response)))
        (is (= (str (:event/id event)) (:event_id body)))
        (is (= "llm_call" (:event_type body)))))))

(deftest ingest-llm-call-failed-201-test
  (let [tenant-id (UUID/randomUUID)
        task-id (UUID/randomUUID)
        event (assoc (sample-event tenant-id task-id) :event/status :failure
                     :event/output-tokens 0)]
    (with-redefs [event-service/record-llm-call! (constantly event)]
      (let [body (json-body (post-llm-call (assoc (sample-wire-body tenant-id task-id)
                                                  :status "failure"
                                                  :output_tokens 0)))]
        (is (= "failure" (:status body)))
        (is (= 0 (:output_tokens body)))))))

(deftest ingest-llm-call-missing-provider-400-test
  (let [tenant-id (UUID/randomUUID)
        task-id (UUID/randomUUID)]
    (assert-validation-400 (dissoc (sample-wire-body tenant-id task-id) :provider)
                           :field "provider")))

(deftest ingest-llm-call-missing-tenant-id-400-test
  (let [tenant-id (UUID/randomUUID)
        task-id (UUID/randomUUID)]
    (assert-validation-400 (dissoc (sample-wire-body tenant-id task-id) :tenant_id)
                           :field "tenant_id")))

(deftest ingest-llm-call-missing-task-id-400-test
  (let [tenant-id (UUID/randomUUID)
        task-id (UUID/randomUUID)]
    (assert-validation-400 (dissoc (sample-wire-body tenant-id task-id) :task_id)
                           :field "task_id")))

(deftest ingest-llm-call-missing-model-400-test
  (let [tenant-id (UUID/randomUUID)
        task-id (UUID/randomUUID)]
    (assert-validation-400 (dissoc (sample-wire-body tenant-id task-id) :model)
                           :field "model")))

(deftest ingest-llm-call-missing-input-tokens-400-test
  (let [tenant-id (UUID/randomUUID)
        task-id (UUID/randomUUID)]
    (assert-validation-400 (dissoc (sample-wire-body tenant-id task-id) :input_tokens)
                           :field "input_tokens")))

(deftest ingest-llm-call-missing-output-tokens-400-test
  (let [tenant-id (UUID/randomUUID)
        task-id (UUID/randomUUID)]
    (assert-validation-400 (dissoc (sample-wire-body tenant-id task-id) :output_tokens)
                           :field "output_tokens")))

(deftest ingest-llm-call-missing-latency-ms-400-test
  (let [tenant-id (UUID/randomUUID)
        task-id (UUID/randomUUID)]
    (assert-validation-400 (dissoc (sample-wire-body tenant-id task-id) :latency_ms)
                           :field "latency_ms")))

(deftest ingest-llm-call-negative-output-tokens-400-test
  (let [tenant-id (UUID/randomUUID)
        task-id (UUID/randomUUID)]
    (assert-validation-400 (assoc (sample-wire-body tenant-id task-id) :output_tokens -1)
                           :field "output_tokens")))

(deftest ingest-llm-call-negative-cached-tokens-400-test
  (let [tenant-id (UUID/randomUUID)
        task-id (UUID/randomUUID)]
    (assert-validation-400 (assoc (sample-wire-body tenant-id task-id) :cached_tokens -1)
                           :field "cached_tokens")))

(deftest ingest-llm-call-negative-latency-ms-400-test
  (let [tenant-id (UUID/randomUUID)
        task-id (UUID/randomUUID)]
    (assert-validation-400 (assoc (sample-wire-body tenant-id task-id) :latency_ms -1)
                           :field "latency_ms")))

(deftest ingest-llm-call-invalid-status-400-test
  (let [tenant-id (UUID/randomUUID)
        task-id (UUID/randomUUID)]
    (assert-validation-400 (assoc (sample-wire-body tenant-id task-id) :status "running")
                           :field "status")))

(deftest ingest-llm-call-task-not-found-404-test
  (let [tenant-id (UUID/randomUUID)
        task-id (UUID/randomUUID)]
    (with-redefs [event-service/record-llm-call!
                 (fn [_ _ _]
                   (throw (errors/not-found-error "Task not found"
                                                  {:details {:task_id task-id}})))]
      (let [response (post-llm-call (sample-wire-body tenant-id task-id))
            body (json-body response)]
        (is (= 404 (:status response)))
        (is (= "task_not_found" (:error body)))))))

(deftest ingest-llm-call-span-not-found-404-test
  (let [tenant-id (UUID/randomUUID)
        task-id (UUID/randomUUID)]
    (with-redefs [event-service/record-llm-call!
                 (fn [_ _ _]
                   (throw (errors/not-found-error "Span not found"
                                                  {:details {:span_id (UUID/randomUUID)}})))]
      (let [body (json-body (post-llm-call (sample-wire-body tenant-id task-id)))]
        (is (= "span_not_found" (:error body)))))))

(deftest ingest-llm-call-conflict-409-test
  (let [tenant-id (UUID/randomUUID)
        task-id (UUID/randomUUID)]
    (with-redefs [event-service/record-llm-call!
                 (fn [_ _ _]
                   (throw (errors/conflict-error "Duplicate external_event_id for tenant")))]
      (let [body (json-body (post-llm-call (sample-wire-body tenant-id task-id)))]
        (is (= "event_conflict" (:error body)))))))

(deftest ingest-llm-call-invalid-json-400-test
  (let [response ((app) (-> (mock/request :post "/v1/events/llm-call")
                            (mock/content-type "application/json")
                            (assoc :body "{invalid}")))]
    (is (= 400 (:status response)))))
