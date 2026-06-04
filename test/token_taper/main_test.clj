;; SPDX-FileCopyrightText: 2026 Haluan Irsad
;; SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Commercial

(ns token-taper.main-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [token-taper.main :as main]
   [token-taper.observability.logging :as logging]
   [token-taper.system.config :as config]
   [token-taper.system.integrant :as system]))

(deftest main-vars-test
  (is (fn? main/start-api!))
  (is (fn? main/run-migrations!)))

(deftest service-start-failed-emitted-on-integrant-error-test
  (binding [logging/*log-sink* (atom [])]
    (with-redefs [system/start-system! (fn [_cfg]
                                         (throw (ex-info "jetty failed" {})))]
      (is (thrown? clojure.lang.ExceptionInfo (main/start-api!)))
      (let [entries (logging/parse-sink-lines @logging/*log-sink*)
            events (set (map :event entries))]
        (is (contains? events "service_starting"))
        (is (contains? events "service_start_failed"))
        (is (not (contains? events "service_started")))
        (let [failed (some #(when (= "service_start_failed" (:event %)) %) entries)]
          (is (= "fatal" (:level failed)))
          (is (= "jetty failed" (:error_message failed))))))))
