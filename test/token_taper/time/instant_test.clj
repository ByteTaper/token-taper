;; SPDX-FileCopyrightText: 2026 Haluan Irsad
;; SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Commercial

(ns token-taper.time.instant-test
  (:require
   [clojure.test :refer [deftest is]]
   [token-taper.task.errors :as errors]
   [token-taper.time.instant :as instant])
  (:import
   [java.sql Timestamp]
   [java.time Instant]))

(deftest parse-instant-string-test
  (is (= (Instant/parse "2026-06-05T10:00:00Z")
         (instant/parse-instant "2026-06-05T10:00:00Z"))))

(deftest require-instant-rejects-nil-test
  (is (thrown? clojure.lang.ExceptionInfo
               (instant/require-instant-field! "occurred_at" nil "bad"))))

(deftest require-instant-rejects-invalid-string-test
  (try
    (instant/require-instant-field! "occurred_at" "not-a-time" "bad")
    (is false "expected validation")
    (catch clojure.lang.ExceptionInfo e
      (is (errors/validation-error? e))
      (is (= [{:field "occurred_at" :reason "invalid"}]
             (:error/details (ex-data e)))))))

(deftest instant->sql-timestamp-roundtrip-test
  (let [inst (Instant/now)
        ts (instant/instant->sql-timestamp inst)]
    (is (instance? Timestamp ts))
    (is (= inst (.toInstant ts)))))
