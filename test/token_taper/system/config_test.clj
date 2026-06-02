;; SPDX-FileCopyrightText: 2026 Haluan Irsad
;; SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Commercial

(ns token-taper.system.config-test
  (:require
   [clojure.test :refer [deftest is]]
   [token-taper.system.config :as config]))

(deftest load-test-config-test
  (let [cfg (config/load-config "resources/config.test.edn")]
    (is (= "token-taper" (get-in cfg [:token-taper/app :service-name])))
    (is (= "test" (get-in cfg [:token-taper/app :environment])))
    (is (= 0 (get-in cfg [:token-taper/http :port])))))

(deftest validate-config-rejects-missing-required-keys-test
  (try
    (config/validate-config! {})
    (is false "expected ex-info")
    (catch clojure.lang.ExceptionInfo e
      (is (re-find #"Missing required config key" (.getMessage e))))))
