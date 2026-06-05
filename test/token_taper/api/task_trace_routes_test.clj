;; SPDX-FileCopyrightText: 2026 Haluan Irsad
;; SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Commercial

(ns token-taper.api.task-trace-routes-test
  (:require
   [clojure.test :refer [deftest is]]
   [jsonista.core :as json]
   [ring.mock.request :as mock]
   [token-taper.api.server :as server]
   [token-taper.task.errors :as errors]
   [token-taper.trace.service :as trace-service]
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

(defn- get-trace [task-id]
  ((app) (mock/request :get (str "/v1/tasks/" task-id "/trace"))))

(defn- sample-trace [task-id]
  (let [started (Instant/parse "2026-06-05T10:00:00Z")
        span-id (UUID/randomUUID)]
    {:task {:task/id task-id
            :tenant/id (UUID/randomUUID)
            :task/status :started
            :task/started-at started
            :task/finished-at nil
            :task/metadata {}}
     :spans [{:span/id span-id
              :task/id task-id
              :span/parent-id nil
              :span/type :workflow
              :span/name "root"
              :span/status :started
              :span/started-at started
              :span/finished-at nil
              :span/metadata {}}]
     :events []}))

(deftest get-trace-empty-200-test
  (let [task-id (UUID/randomUUID)
        trace {:task {:task/id task-id
                     :tenant/id (UUID/randomUUID)
                     :task/status :started
                     :task/started-at (Instant/now)
                     :task/metadata {}}
               :spans []
               :events []}]
    (with-redefs [trace-service/get-task-trace! (fn [_ _ _] trace)]
      (let [response (get-trace task-id)
            body (json-body response)]
        (is (= 200 (:status response)))
        (is (= (str task-id) (get-in body [:task :task_id])))
        (is (= [] (:spans body)))
        (is (= [] (:events body)))))))

(deftest get-trace-with-spans-200-test
  (let [task-id (UUID/randomUUID)
        trace (sample-trace task-id)]
    (with-redefs [trace-service/get-task-trace! (constantly trace)]
      (let [body (json-body (get-trace task-id))]
        (is (= 1 (count (:spans body))))
        (is (= "workflow" (:span_type (first (:spans body)))))))))

(deftest get-trace-invalid-uuid-400-test
  (let [response (get-trace "bad-uuid")
        body (json-body response)]
    (is (= 400 (:status response)))
    (is (= "validation_error" (:error body)))))

(deftest get-trace-not-found-404-test
  (let [task-id (UUID/randomUUID)]
    (with-redefs [trace-service/get-task-trace!
                 (fn [_ _ _]
                   (throw (errors/not-found-error "Task not found")))]
      (let [body (json-body (get-trace task-id))]
        (is (= "task_not_found" (:error body)))))))
