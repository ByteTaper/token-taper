;; SPDX-FileCopyrightText: 2026 Haluan Irsad
;; SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Commercial

(ns token-taper.event.service-test
  (:require
   [clojure.test :refer [deftest is]]
   [token-taper.event.repository :as repo]
   [token-taper.event.service :as service]
   [token-taper.test-support.logging :as log-support])
  (:import
   [java.time Instant]
   [java.util UUID]))

(defn- wire-request
  [tenant-id task-id]
  {:tenant_id (str tenant-id)
   :task_id (str task-id)
   :provider "anthropic"
   :model "claude-sonnet"
   :input_tokens 100
   :output_tokens 50
   :latency_ms 1000
   :status "success"})

(deftest record-llm-call-calls-repository-test
  (let [tenant-id (UUID/randomUUID)
        task-id (UUID/randomUUID)
        event {:event/id (UUID/randomUUID)
               :tenant/id tenant-id
               :task/id task-id
               :event/type :llm_call
               :event/status :success}]
    (with-redefs [repo/create-event!
                 (fn [_db input]
                   (is (= :llm_call (:event_type input)))
                   (is (instance? Instant (:occurred_at input)))
                   event)]
      (is (= event
             (service/record-llm-call!
              (Object.)
              (wire-request tenant-id task-id)
              {:logger (log-support/test-logger)}))))))
