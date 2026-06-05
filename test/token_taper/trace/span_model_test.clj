;; SPDX-FileCopyrightText: 2026 Haluan Irsad
;; SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Commercial

(ns token-taper.trace.span-model-test
  (:require
   [clojure.test :refer [deftest is]]
   [token-taper.trace.span-model :as model])
  (:import
   [java.time Instant]
   [java.util UUID]))

(def sample-row
  {:id (UUID/randomUUID)
   :tenant-id (UUID/randomUUID)
   :task-id (UUID/randomUUID)
   :parent-span-id nil
   :external-span-id "span_01"
   :span-type "agent_step"
   :name "retrieve_context"
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

(deftest span-type-validity-test
  (is (model/valid-span-type? :llm_call))
  (is (not (model/valid-span-type? :unknown))))

(deftest status-db-roundtrip-test
  (is (= :agent_step (model/db->span-type (model/span-type->db :agent_step)))))

(deftest row-span-roundtrip-test
  (let [span (model/row->span sample-row)]
    (is (= (:id sample-row) (:span/id span)))
    (is (= :started (:span/status span)))
    (is (= :agent_step (:span/type span)))
    (is (= "bar" (get-in span [:span/metadata :foo])))))

(deftest validate-started-invariants-test
  (let [span {:span/status :started :span/finished-at nil}]
    (is (= span (model/validate-span-invariants! span)))))

(deftest validate-terminal-requires-finished-at-test
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"finished_at"
                        (model/validate-span-invariants!
                         {:span/status :finished :span/finished-at nil}))))

(deftest validate-started-rejects-finished-at-test
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"finished_at"
                        (model/validate-span-invariants!
                         {:span/status :started
                          :span/finished-at (Instant/now)}))))

(deftest validate-finish-transition-rejects-terminal-test
  (let [span {:span/id (UUID/randomUUID)
              :span/status :finished}]
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"already terminal"
                          (model/validate-finish-transition! span :failed)))))

(deftest merge-metadata-test
  (is (= {:a 1 :b 2} (model/merge-metadata {:a 1} {:b 2})))
  (is (= {:a 1} (model/merge-metadata {:a 1} nil))))
