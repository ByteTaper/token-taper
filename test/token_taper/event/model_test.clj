;; SPDX-FileCopyrightText: 2026 Haluan Irsad
;; SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Commercial

(ns token-taper.event.model-test
  (:require
   [clojure.test :refer [deftest is]]
   [token-taper.event.model :as model])
  (:import
   [java.time Instant]
   [java.util UUID]))

(def sample-row
  {:id (UUID/randomUUID)
   :tenant-id (UUID/randomUUID)
   :task-id (UUID/randomUUID)
   :span-id (UUID/randomUUID)
   :external-event-id "evt_01"
   :event-type "llm_call"
   :status "success"
   :provider "anthropic"
   :model "claude-sonnet"
   :tool-name nil
   :input-tokens 3000
   :output-tokens 700
   :cached-tokens nil
   :latency-ms 3400
   :retry-count nil
   :cache-hit nil
   :error-code nil
   :error-message nil
   :metadata "{\"foo\":\"bar\"}"
   :occurred-at (Instant/now)
   :created-at (Instant/now)})

(deftest event-type-validity-test
  (is (model/valid-event-type? :llm_call))
  (is (not (model/valid-event-type? :unknown))))

(deftest status-validity-test
  (is (model/valid-status? :success))
  (is (model/valid-status? :failure))
  (is (not (model/valid-status? :started))))

(deftest type-db-roundtrip-test
  (is (= :llm_call (model/db->event-type (model/event-type->db :llm_call)))))

(deftest row-event-roundtrip-test
  (let [event (model/row->event sample-row)]
    (is (= (:id sample-row) (:event/id event)))
    (is (= :llm_call (:event/type event)))
    (is (= :success (:event/status event)))
    (is (= "bar" (get-in event [:event/metadata :foo])))))

(deftest validate-non-negative-counts-test
  (let [event (model/row->event sample-row)]
    (is (= event (model/validate-non-negative-counts! event)))))

(deftest validate-negative-input-tokens-test
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"input_tokens"
                        (model/validate-non-negative-counts!
                         {:event/input-tokens -1}))))

(deftest validate-negative-output-tokens-test
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"output_tokens"
                        (model/validate-non-negative-counts!
                         {:event/output-tokens -1}))))

(deftest validate-negative-cached-tokens-test
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"cached_tokens"
                        (model/validate-non-negative-counts!
                         {:event/cached-tokens -1}))))

(deftest validate-negative-latency-test
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"latency_ms"
                        (model/validate-non-negative-counts!
                         {:event/latency-ms -5}))))

(deftest validate-negative-retry-count-test
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"retry_count"
                        (model/validate-non-negative-counts!
                         {:event/retry-count -1}))))
