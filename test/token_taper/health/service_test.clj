;; SPDX-FileCopyrightText: 2026 Haluan Irsad
;; SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Commercial

(ns token-taper.health.service-test
  (:require
   [clojure.test :refer [deftest is]]
   [token-taper.db.health :as db-health]
   [token-taper.health.checks :as checks]
   [token-taper.health.service :as service]))

(def ^:private base-health-system
  {:app {:service-name "token-taper"
         :service-version "0.1.0-SNAPSHOT"
         :environment "test"
         :status :started}
   :config {:status :loaded}
   :datasource (Object.)
   :health-config {:database-timeout-ms 1000}})

(deftest live-always-alive-test
  (let [{:keys [http-status body]} (service/live base-health-system)]
    (is (= 200 http-status))
    (is (= "alive" (:status body)))
    (is (= "tokentaper" (:service body)))))

(deftest ready-all-checks-pass-test
  (with-redefs [db-health/database-ready? (constantly (checks/ok))
                db-health/migrations-ready? (constantly (checks/ok))]
    (let [{:keys [http-status body]} (service/ready base-health-system)]
      (is (= 200 http-status))
      (is (= "ready" (:status body)))
      (is (= "ok" (get-in body [:checks :config :status])))
      (is (= "ok" (get-in body [:checks :database :status]))))))

(deftest ready-database-failure-test
  (with-redefs [db-health/database-ready? (constantly (checks/error :database-unreachable))
                db-health/migrations-ready? (constantly (checks/ok))]
    (let [{:keys [http-status body]} (service/ready base-health-system)]
      (is (= 503 http-status))
      (is (= "not_ready" (:status body)))
      (is (= "error" (get-in body [:checks :database :status])))
      (is (= "database_unreachable" (get-in body [:checks :database :reason]))))))

(deftest ready-migrations-failure-test
  (with-redefs [db-health/database-ready? (constantly (checks/ok))
                db-health/migrations-ready? (constantly (checks/error :migrations-unavailable))]
    (let [{:keys [http-status body]} (service/ready base-health-system)]
      (is (= 503 http-status))
      (is (= "not_ready" (:status body)))
      (is (= "error" (get-in body [:checks :migrations :status]))))))

(deftest ready-migrations-unknown-when-database-fails-test
  (with-redefs [db-health/database-ready? (constantly (checks/error :database-unreachable))
                db-health/migrations-ready? (constantly (checks/ok))]
    (let [{:keys [body]} (service/ready base-health-system)]
      (is (= "unknown" (get-in body [:checks :migrations :status])))
      (is (= "database_unreachable" (get-in body [:checks :migrations :reason]))))))
