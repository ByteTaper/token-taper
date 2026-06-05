;; SPDX-FileCopyrightText: 2026 Haluan Irsad
;; SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Commercial

(ns token-taper.trace.service-test
  (:require
   [clojure.test :refer [deftest is]]
   [token-taper.event.repository :as event-repo]
   [token-taper.task.repository :as task-repo]
   [token-taper.trace.service :as trace-service]
   [token-taper.trace.span-repository :as span-repo]
   [token-taper.test-support.logging :as log-support])
  (:import
   [java.util UUID]))

(deftest get-task-trace-empty-spans-and-events-test
  (let [task-id (UUID/randomUUID)
        task {:task/id task-id}]
    (with-redefs [task-repo/find-task-by-id (fn [_ id]
                                              (is (= task-id id))
                                              task)
                  span-repo/find-spans-by-task-id (fn [_ id]
                                                    (is (= task-id id))
                                                    [])
                  event-repo/find-events-by-task-id (fn [_ id]
                                                      (is (= task-id id))
                                                      [])]
      (let [result (trace-service/get-task-trace!
                    (Object.)
                    (str task-id)
                    {:logger (log-support/test-logger)})]
        (is (= task (:task result)))
        (is (= [] (:spans result)))
        (is (= [] (:events result)))))))

(deftest get-task-trace-not-found-test
  (let [task-id (UUID/randomUUID)]
    (with-redefs [task-repo/find-task-by-id (constantly nil)]
      (is (thrown? clojure.lang.ExceptionInfo
                   (trace-service/get-task-trace!
                    (Object.)
                    (str task-id)
                    {:logger (log-support/test-logger)}))))))
