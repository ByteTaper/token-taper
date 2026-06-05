;; SPDX-FileCopyrightText: 2026 Haluan Irsad
;; SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Commercial

(ns token-taper.task.service-test
  (:require
   [clojure.test :refer [deftest is]]
   [token-taper.task.repository :as repo]
   [token-taper.task.service :as service]
   [token-taper.test-support.logging :as log-support])
  (:import
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
