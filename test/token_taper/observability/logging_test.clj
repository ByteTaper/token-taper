;; SPDX-FileCopyrightText: 2026 Haluan Irsad
;; SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Commercial

(ns token-taper.observability.logging-test
  (:require
   [clojure.string :as str]
   [clojure.test :refer [deftest is]]
   [token-taper.observability.logging :as logging]))

(def ^:private ctx (logging/test-context))

(deftest build-event-includes-standard-fields-test
  (let [event (logging/build-event ctx :info :test_event {:foo "bar"})]
    (is (string? (:timestamp event)))
    (is (= "info" (:level event)))
    (is (= "test_event" (:event event)))
    (is (= "tokentaper" (:service event)))
    (is (= "0.1.0-SNAPSHOT" (:version event)))
    (is (= "test" (:env event)))
    (is (= "bar" (:foo event)))))

(deftest redact-sensitive-fields-test
  (let [redacted (logging/redact-map {:authorization "secret"
                                      :x-api-key "key"
                                      :database_url "postgres://x"
                                      :jdbc_url "jdbc:postgresql://x"}
                                     logging/default-redact-fields)]
    (is (= "[REDACTED]" (:authorization redacted)))
    (is (= "[REDACTED]" (:x-api-key redacted)))
    (is (= "[REDACTED]" (:database_url redacted)))
    (is (= "[REDACTED]" (:jdbc_url redacted)))))

(deftest request-body-excluded-from-events-test
  (let [redacted (logging/redact-map {:body "payload" :request-body "a" :response-body "b"}
                                     logging/default-redact-fields)]
    (is (nil? (:body redacted)))
    (is (nil? (:request-body redacted)))
    (is (nil? (:response-body redacted)))))

(deftest build-error-fields-test
  (let [t (ex-info "boom" {})
        fields (logging/build-error-fields ctx t)]
    (is (= "clojure.lang.ExceptionInfo" (:error_class fields)))
    (is (= "boom" (:error_message fields)))))

(deftest emit-produces-single-line-ndjson-test
  (binding [logging/*log-sink* (atom [])]
    (logging/info! ctx :test_line {:ok true})
    (let [line (first @logging/*log-sink*)]
      (is (string? line))
      (is (nil? (re-find #"\n" (str/trim line)))))))
