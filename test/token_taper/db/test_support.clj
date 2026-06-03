;; SPDX-FileCopyrightText: 2026 Haluan Irsad
;; SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Commercial

(ns token-taper.db.test-support
  (:require
   [token-taper.system.config :as config]))

(defn integration-db-available?
  []
  (boolean (System/getenv "TOKEN_TAPER_TEST_DATABASE_URL")))

(defn load-test-datasource-config
  []
  (get (config/load-config "resources/config.test.edn")
       :token-taper.db/datasource))

(def valid-unit-config
  {:jdbc-url "jdbc:postgresql://localhost:5432/token_taper_test"
   :username "token_taper"
   :password "token_taper"
   :maximum-pool-size 2
   :minimum-idle 0
   :connection-timeout-ms 5000
   :validation-timeout-ms 1000
   :idle-timeout-ms 10000
   :max-lifetime-ms 30000
   :auto-commit true})
