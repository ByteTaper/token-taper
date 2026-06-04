;; SPDX-FileCopyrightText: 2026 Haluan Irsad
;; SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Commercial

(ns token-taper.db.health-test
  (:require
   [clojure.test :refer [deftest is]]
   [token-taper.db.datasource :as datasource]
   [token-taper.db.health :as health]
   [token-taper.db.test-support :as support]
   [token-taper.health.checks :as checks]))

(deftest database-ready-returns-error-for-unreachable-database-test
  (let [cfg (assoc support/valid-unit-config
                   :jdbc-url "jdbc:postgresql://127.0.0.1:1/nonexistent"
                   :connection-timeout-ms 1000
                   :validation-timeout-ms 500)
        ds (datasource/make-datasource cfg)]
    (try
      (let [result (health/database-ready? ds {:timeout-ms 1000})]
        (is (checks/error? result))
        (is (= :database-unreachable (:reason result))))
      (finally
        (datasource/close-datasource! ds)))))

(deftest database-ready-returns-error-for-closed-datasource-test
  (let [ds (datasource/make-datasource support/valid-unit-config)]
    (datasource/close-datasource! ds)
    (is (checks/error? (health/database-ready? ds)))))

(deftest database-ready-returns-error-for-nil-datasource-test
  (is (checks/error? (health/database-ready? nil))))

(deftest migrations-ready-returns-unknown-for-nil-datasource-test
  (let [result (health/migrations-ready? nil)]
    (is (checks/unknown? result))
    (is (= :database-unreachable (:reason result)))))

(deftest migrations-ready-returns-unknown-for-closed-datasource-test
  (let [ds (datasource/make-datasource support/valid-unit-config)]
    (datasource/close-datasource! ds)
    (let [result (health/migrations-ready? ds)]
      (is (checks/unknown? result))
      (is (= :database-unreachable (:reason result))))))

(deftest check-ready-returns-ok-shape-for-success-test
  (with-redefs [health/database-ready? (constantly (checks/ok))]
    (let [result (health/check-ready (Object.))]
      (is (= :ok (:status result)))
      (is (= :database (:component result))))))

(deftest ^:integration database-ready-returns-ok-when-database-reachable-test
  (when (support/integration-db-available?)
    (let [ds (datasource/make-datasource (support/load-test-datasource-config))]
      (try
        (is (checks/ok? (health/database-ready? ds)))
        (finally
          (datasource/close-datasource! ds))))))

(deftest ^:integration migrations-ready-returns-ok-when-schema-migrations-exists-test
  (when (support/integration-db-available?)
    (let [ds (datasource/make-datasource (support/load-test-datasource-config))]
      (try
        (is (checks/ok? (health/migrations-ready? ds)))
        (finally
          (datasource/close-datasource! ds))))))
