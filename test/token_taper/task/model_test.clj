;; SPDX-FileCopyrightText: 2026 Haluan Irsad
;; SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Commercial

(ns token-taper.task.model-test
  (:require
   [clojure.test :refer [deftest is]]
   [token-taper.task.model :as model])
  (:import
   [java.time Instant]
   [java.util UUID]))

(def sample-row
  {:id (UUID/randomUUID)
   :tenant-id (UUID/randomUUID)
   :external-task-id "ext-1"
   :workflow "support_ticket_agent"
   :task-type "agentic_workflow"
   :status "started"
   :started-at (Instant/now)
   :finished-at nil
   :metadata "{\"foo\":\"bar\"}"
   :created-at (Instant/now)
   :updated-at (Instant/now)})

(deftest status-predicates-test
  (is (model/started? :started))
  (is (model/terminal? :finished))
  (is (model/terminal? :failed))
  (is (model/terminal? :cancelled))
  (is (not (model/terminal? :started))))

(deftest status-db-roundtrip-test
  (is (= :started (model/db->status (model/status->db :started)))))

(deftest row-task-roundtrip-test
  (let [task (model/row->task sample-row)]
    (is (= (:id sample-row) (:task/id task)))
    (is (= :started (:task/status task)))
    (is (= "ext-1" (:task/external-id task)))
    (is (= "bar" (get-in task [:task/metadata :foo])))))

(deftest validate-started-invariants-test
  (let [task {:task/status :started :task/finished-at nil}]
    (is (= task (model/validate-task-invariants! task)))))

(deftest validate-terminal-requires-finished-at-test
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"finished_at"
                        (model/validate-task-invariants!
                         {:task/status :finished :task/finished-at nil}))))

(deftest validate-started-rejects-finished-at-test
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"finished_at"
                        (model/validate-task-invariants!
                         {:task/status :started
                          :task/finished-at (Instant/now)}))))

(deftest merge-metadata-test
  (is (= {:a 1 :b 2} (model/merge-metadata {:a 1} {:b 2})))
  (is (= {:a 1} (model/merge-metadata {:a 1} nil))))

(deftest validate-finish-transition-rejects-terminal-test
  (let [task {:task/id (UUID/randomUUID) :task/status :finished}]
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"already terminal"
                          (model/validate-finish-transition! task :finished)))))
