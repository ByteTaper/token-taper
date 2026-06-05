;; SPDX-FileCopyrightText: 2026 Haluan Irsad
;; SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Commercial

(ns token-taper.task.service-test
  (:require
   [clojure.test :refer [deftest is]]
   [token-taper.task.repository :as repo]
   [token-taper.task.service :as service]
   [token-taper.test-support.logging :as log-support])
  (:import
   [java.time Instant]
   [java.util UUID]))

(deftest start-task-calls-repository-test
  (let [tenant-id (UUID/randomUUID)
        task {:task/id (UUID/randomUUID)
              :tenant/id tenant-id
              :task/status :started
              :task/metadata {}}]
    (with-redefs [repo/create-task! (fn [_db input]
                                      (is (= tenant-id (:tenant_id input)))
                                      task)]
      (is (= task
             (service/start-task!
              (Object.)
              {:tenant_id (str tenant-id)}
              {:logger (log-support/test-logger)}))))))

(deftest finish-task-defaults-finished-at-test
  (let [task-id (UUID/randomUUID)
        finished-at (atom nil)
        task {:task/id task-id
              :tenant/id (UUID/randomUUID)
              :task/status :finished
              :task/finished-at (Instant/now)
              :task/metadata {}}]
    (with-redefs [repo/finish-task! (fn [_db id input]
                                      (is (= task-id id))
                                      (reset! finished-at (:finished_at input))
                                      (is (instance? Instant @finished-at))
                                      task)]
      (is (= task
             (service/finish-task!
              (Object.)
              (str task-id)
              {:status :finished}
              {:logger (log-support/test-logger)}))))))

(deftest get-task-returns-task-test
  (let [task-id (UUID/randomUUID)
        task {:task/id task-id :task/status :started}]
    (with-redefs [repo/find-task-by-id (fn [_db id]
                                        (is (= task-id id))
                                        task)]
      (is (= task
             (service/get-task!
              (Object.)
              (str task-id)
              {:logger (log-support/test-logger)}))))))

(deftest get-task-not-found-test
  (let [task-id (UUID/randomUUID)]
    (with-redefs [repo/find-task-by-id (constantly nil)]
      (is (thrown? clojure.lang.ExceptionInfo
                   (service/get-task!
                    (Object.)
                    (str task-id)
                    {:logger (log-support/test-logger)}))))))
