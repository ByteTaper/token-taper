;; SPDX-FileCopyrightText: 2026 Haluan Irsad
;; SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Commercial

(ns token-taper.api.task-start-routes-test
  (:require
   [clojure.test :refer [deftest is]]
   [jsonista.core :as json]
   [ring.mock.request :as mock]
   [token-taper.api.server :as server]
   [token-taper.task.service :as task-service]
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

(defn- post-start [body]
  ((app) (-> (mock/request :post "/v1/tasks/start")
             (mock/content-type "application/json")
             (mock/json-body body))))

(deftest start-task-minimal-201-test
  (let [tenant-id (UUID/randomUUID)
        task-id (UUID/randomUUID)
        task {:task/id task-id
              :tenant/id tenant-id
              :task/external-id nil
              :task/workflow nil
              :task/type nil
              :task/status :started
              :task/started-at (Instant/now)
              :task/finished-at nil
              :task/metadata {}}]
    (with-redefs [task-service/start-task! (fn [_ _ _] task)]
      (let [response (post-start {:tenant_id (str tenant-id)})
            body (json-body response)]
        (is (= 201 (:status response)))
        (is (= (str task-id) (:task_id body)))
        (is (= "started" (:status body)))
        (is (= {} (:metadata body)))))))

(deftest start-task-full-payload-201-test
  (let [tenant-id (UUID/randomUUID)
        task-id (UUID/randomUUID)
        started (Instant/parse "2026-06-05T10:00:00Z")
        task {:task/id task-id
              :tenant/id tenant-id
              :task/external-id "task_01"
              :task/workflow "support_ticket_agent"
              :task/type "agentic_workflow"
              :task/status :started
              :task/started-at started
              :task/finished-at nil
              :task/metadata {:environment "dev"}}]
    (with-redefs [task-service/start-task! (constantly task)]
      (let [body (json-body (post-start {:tenant_id (str tenant-id)
                                         :external_task_id "task_01"
                                         :workflow "support_ticket_agent"
                                         :task_type "agentic_workflow"
                                         :started_at "2026-06-05T10:00:00Z"
                                         :metadata {:environment "dev"}}))]
        (is (= (str task-id) (:task_id body)))
        (is (= "task_01" (:external_task_id body)))
        (is (= "dev" (get-in body [:metadata :environment])))))))

(deftest start-task-missing-tenant-400-test
  (let [response (post-start {})
        body (json-body response)]
    (is (= 400 (:status response)))
    (is (= "validation_error" (:error body)))))

(deftest start-task-invalid-started-at-400-test
  (let [response (post-start {:tenant_id (str (UUID/randomUUID))
                              :started_at "not-a-time"})
        body (json-body response)]
    (is (= 400 (:status response)))
    (is (= "validation_error" (:error body)))
    (is (= "started_at" (:field (first (:details body)))))
    (is (= "invalid" (:reason (first (:details body)))))))

(deftest start-task-invalid-json-400-test
  (let [response ((app) (-> (mock/request :post "/v1/tasks/start")
                           (mock/content-type "application/json")
                           (assoc :body "{invalid}")))
        body (json-body response)]
    (is (= 400 (:status response)))
    (is (= "validation_error" (:error body)))))

(deftest start-task-conflict-409-test
  (with-redefs [task-service/start-task!
               (fn [_ _ _]
                 (throw (ex-info "Duplicate external_task_id for tenant"
                                 {:error/kind :conflict
                                  :error/message "Duplicate external_task_id for tenant"
                                  :error/details {:external_task_id "dup"}})))]
    (let [response (post-start {:tenant_id (str (UUID/randomUUID))
                                :external_task_id "dup"})
          body (json-body response)]
      (is (= 409 (:status response)))
      (is (= "task_conflict" (:error body))))))
