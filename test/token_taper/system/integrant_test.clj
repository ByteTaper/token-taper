;; SPDX-FileCopyrightText: 2026 Haluan Irsad
;; SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Commercial

(ns token-taper.system.integrant-test
  (:require
   [clojure.test :refer [deftest is]]
   [token-taper.system.components]
   [token-taper.system.config :as config]
   [token-taper.system.integrant :as system]))

(deftest start-and-stop-system-test
  (let [cfg (config/load-config "resources/config.test.edn")
        sys (system/start-system! cfg)]
    (try
      (is (= :started (get-in sys [:token-taper/app :status])))
      (is (= :started (get-in sys [:token-taper/http-server :status])))
      (is (pos? (get-in sys [:token-taper/http-server :port])))
      (finally
        (system/stop-system! sys)))))
