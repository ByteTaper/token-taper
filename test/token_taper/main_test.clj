// SPDX-FileCopyrightText: 2026 Haluan Irsad
// SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Commercial

(ns token-taper.main-test
  (:require
   [clojure.test :refer [deftest is]]
   [token-taper.main :as main]))

(deftest system-info-test
  (let [info (main/system-info)]
    (is (= "token-taper" (:service info)))
    (is (string? (:version info)))
    (is (seq (:version info)))))
