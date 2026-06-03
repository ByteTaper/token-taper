;; SPDX-FileCopyrightText: 2026 Haluan Irsad
;; SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Commercial

(ns token-taper.db.jdbc-test
  (:require
   [clojure.test :refer [deftest is]]
   [token-taper.db.datasource :as datasource]
   [token-taper.db.jdbc :as jdbc]
   [token-taper.db.test-support :as support]))

(deftest ^:integration execute-one-select-1-test
  (when (support/integration-db-available?)
    (let [ds (datasource/make-datasource (support/load-test-datasource-config))]
      (try
        (is (= 1 (:ok (jdbc/execute-one! ds ["SELECT 1 AS ok"]))))
        (finally
          (datasource/close-datasource! ds))))))
