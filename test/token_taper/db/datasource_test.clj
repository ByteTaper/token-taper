;; SPDX-FileCopyrightText: 2026 Haluan Irsad
;; SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Commercial

(ns token-taper.db.datasource-test
  (:require
   [clojure.test :refer [deftest is]]
   [integrant.core :as ig]
   [token-taper.db.datasource :as datasource]
   [token-taper.db.test-support :as support]
   [token-taper.system.components]))

(deftest validate-config-passes-for-valid-config-test
  (is (= support/valid-unit-config
         (datasource/validate-config! support/valid-unit-config))))

(deftest validate-config-fails-for-missing-jdbc-url-test
  (is (thrown-with-msg?
       clojure.lang.ExceptionInfo
       #"Missing required database config key"
       (datasource/validate-config! (dissoc support/valid-unit-config :jdbc-url)))))

(deftest validate-config-fails-when-minimum-idle-exceeds-maximum-pool-size-test
  (is (thrown-with-msg?
       clojure.lang.ExceptionInfo
       #"minimum idle must not exceed maximum pool size"
       (datasource/validate-config!
        (assoc support/valid-unit-config :minimum-idle 5 :maximum-pool-size 2)))))

(deftest datasource-lifecycle-test
  (let [ds (datasource/make-datasource support/valid-unit-config)]
    (try
      (is (datasource/datasource? ds))
      (finally
        (is (nil? (datasource/close-datasource! ds)))
        (is (nil? (datasource/close-datasource! ds)))))))

(deftest integrant-datasource-lifecycle-test
  (let [sys (ig/init {:token-taper.db/datasource support/valid-unit-config})
        ds (get sys :token-taper.db/datasource)]
    (try
      (is (datasource/datasource? ds))
      (finally
        (ig/halt! sys)))))

(deftest ^:integration datasource-from-test-config-test
  (when (support/integration-db-available?)
    (let [cfg (support/load-test-datasource-config)
          ds (datasource/make-datasource cfg)]
      (try
        (is (datasource/datasource? ds))
        (finally
          (datasource/close-datasource! ds))))))
