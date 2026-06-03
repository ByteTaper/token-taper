;; SPDX-FileCopyrightText: 2026 Haluan Irsad
;; SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Commercial

(ns token-taper.db.health-test
  (:require
   [clojure.test :refer [deftest is]]
   [token-taper.db.datasource :as datasource]
   [token-taper.db.health :as health]
   [token-taper.db.test-support :as support]))

(deftest check-ready-returns-error-for-unreachable-database-test
  (let [cfg (assoc support/valid-unit-config
                   :jdbc-url "jdbc:postgresql://127.0.0.1:1/nonexistent"
                   :connection-timeout-ms 1000
                   :validation-timeout-ms 500)
        ds (datasource/make-datasource cfg)]
    (try
      (let [result (health/check-ready ds)]
        (is (= :error (:status result)))
        (is (= :database (:component result)))
        (is (string? (get-in result [:error :message]))))
      (finally
        (datasource/close-datasource! ds)))))

(deftest check-ready-returns-error-for-closed-datasource-test
  (let [ds (datasource/make-datasource support/valid-unit-config)]
    (datasource/close-datasource! ds)
    (is (= :error (:status (health/check-ready ds))))))

(deftest ^:integration check-ready-returns-ok-when-database-reachable-test
  (when (support/integration-db-available?)
    (let [ds (datasource/make-datasource (support/load-test-datasource-config))]
      (try
        (is (= :ok (:status (health/check-ready ds))))
        (is (= :database (:component (health/check-ready ds))))
        (finally
          (datasource/close-datasource! ds))))))
