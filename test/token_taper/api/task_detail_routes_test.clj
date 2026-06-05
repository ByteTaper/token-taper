;; SPDX-FileCopyrightText: 2026 Haluan Irsad
;; SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Commercial

(ns token-taper.api.task-detail-routes-test
  (:require
   [clojure.test :refer [deftest is]]
   [jsonista.core :as json]
   [ring.mock.request :as mock]
   [token-taper.api.server :as server]
   [token-taper.task.errors :as errors]
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

(defn- get-task [task-id]
  ((app) (mock/request :get (str "/v1/tasks/" task-id))))

(defn- sample-task [task-id status]
  (let [started (Instant/parse "2026-06-05T10:00:00Z")
        finished (when (not= status :started) (Instant/parse "2026-06-05T10:10:00Z"))
        created (Instant/parse "2026-06-05T10:00:00Z")
        updated (Instant/parse "2026-06-05T10:10:00Z")]
    {:task/id task-id
     :tenant/id (UUID/randomUUID)
     :task/external-id "task_01"
     :task/workflow "wf"
     :task/type "agentic_workflow"
     :task/status status
     :task/started-at started
     :task/finished-at finished
     :task/metadata {:environment "dev"}
     :task/created-at created
     :task/updated-at updated}))

(deftest get-task-started-200-test
  (let [task-id (UUID/randomUUID)
        task (sample-task task-id :started)]
    (with-redefs [task-service/get-task! (fn [_ _ _] task)]
      (let [response (get-task task-id)
            body (json-body response)]
        (is (= 200 (:status response)))
        (is (= (str task-id) (:task_id body)))
        (is (= "started" (:status body)))
        (is (= "dev" (get-in body [:metadata :environment])))))))

(deftest get-task-finished-200-test
  (let [task-id (UUID/randomUUID)
        task (sample-task task-id :finished)]
    (with-redefs [task-service/get-task! (constantly task)]
      (let [response (get-task task-id)
            body (json-body response)]
        (is (= 200 (:status response)))
        (is (= "finished" (:status body)))
        (is (some? (:finished_at body)))))))

(deftest get-task-invalid-uuid-400-test
  (let [response (get-task "not-a-uuid")
        body (json-body response)]
    (is (= 400 (:status response)))
    (is (= "validation_error" (:error body)))
    (is (= "invalid_uuid" (:reason (first (:details body)))))))

(deftest get-task-not-found-404-test
  (let [task-id (UUID/randomUUID)]
    (with-redefs [task-service/get-task!
                 (fn [_ _ _]
                   (throw (errors/not-found-error "Task not found")))]
      (let [response (get-task task-id)
            body (json-body response)]
        (is (= 404 (:status response)))
        (is (= "task_not_found" (:error body)))))))
