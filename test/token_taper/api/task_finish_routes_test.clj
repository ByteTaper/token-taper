;; SPDX-FileCopyrightText: 2026 Haluan Irsad
;; SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Commercial

(ns token-taper.api.task-finish-routes-test
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

(defn- post-finish [task-id body]
  ((app) (-> (mock/request :post (str "/v1/tasks/" task-id "/finish"))
             (mock/content-type "application/json")
             (mock/json-body body))))

(defn- sample-finished-task [task-id]
  {:task/id task-id
   :tenant/id (UUID/randomUUID)
   :task/external-id "task_01"
   :task/workflow "support_ticket_agent"
   :task/type "agentic_workflow"
   :task/status :finished
   :task/started-at (Instant/parse "2026-06-05T10:00:00Z")
   :task/finished-at (Instant/parse "2026-06-05T10:10:00Z")
   :task/metadata {:result "ok"}})

(deftest finish-task-finished-200-test
  (let [task-id (UUID/randomUUID)
        task (sample-finished-task task-id)]
    (with-redefs [task-service/finish-task! (fn [_ _ _ _] task)]
      (let [response (post-finish task-id {:status "finished"})
            body (json-body response)]
        (is (= 200 (:status response)))
        (is (= (str task-id) (:task_id body)))
        (is (= "finished" (:status body)))))))

(deftest finish-task-failed-200-test
  (let [task-id (UUID/randomUUID)
        task (assoc (sample-finished-task task-id) :task/status :failed)]
    (with-redefs [task-service/finish-task! (constantly task)]
      (let [body (json-body (post-finish task-id {:status "failed"}))]
        (is (= "failed" (:status body)))))))

(deftest finish-task-cancelled-200-test
  (let [task-id (UUID/randomUUID)
        task (assoc (sample-finished-task task-id) :task/status :cancelled)]
    (with-redefs [task-service/finish-task! (constantly task)]
      (let [body (json-body (post-finish task-id {:status "cancelled"}))]
        (is (= "cancelled" (:status body)))))))

(deftest finish-task-invalid-task-id-400-test
  (let [response (post-finish "not-a-uuid" {:status "finished"})
        body (json-body response)]
    (is (= 400 (:status response)))
    (is (= "validation_error" (:error body)))
    (is (= "task_id" (:field (first (:details body)))))))

(deftest finish-task-invalid-status-400-test
  (let [task-id (UUID/randomUUID)
        response (post-finish task-id {:status "started"})
        body (json-body response)]
    (is (= 400 (:status response)))
    (is (= "validation_error" (:error body)))))

(deftest finish-task-invalid-finished-at-400-test
  (let [task-id (UUID/randomUUID)
        response (post-finish task-id {:status "finished" :finished_at "bad"})
        body (json-body response)]
    (is (= 400 (:status response)))
    (is (= "finished_at" (:field (first (:details body)))))))

(deftest finish-task-invalid-json-400-test
  (let [task-id (UUID/randomUUID)
        response ((app) (-> (mock/request :post (str "/v1/tasks/" task-id "/finish"))
                            (mock/content-type "application/json")
                            (assoc :body "{invalid}")))
        body (json-body response)]
    (is (= 400 (:status response)))
    (is (= "validation_error" (:error body)))))

(deftest finish-task-not-found-404-test
  (let [task-id (UUID/randomUUID)]
    (with-redefs [task-service/finish-task!
                  (fn [_ _ _ _]
                    (throw (errors/not-found-error "Task not found")))]
      (let [response (post-finish task-id {:status "finished"})
            body (json-body response)]
        (is (= 404 (:status response)))
        (is (= "task_not_found" (:error body)))
        (is (= "not_found" (:reason (first (:details body)))))))))

(deftest finish-task-conflict-409-test
  (let [task-id (UUID/randomUUID)]
    (with-redefs [task-service/finish-task!
                  (fn [_ _ _ _]
                    (throw (errors/conflict-error "Task is not in started status")))]
      (let [response (post-finish task-id {:status "finished"})
            body (json-body response)]
        (is (= 409 (:status response)))
        (is (= "task_conflict" (:error body)))
        (is (= "already_terminal" (:reason (first (:details body)))))))))
