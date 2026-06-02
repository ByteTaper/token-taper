;; SPDX-FileCopyrightText: 2026 Haluan Irsad
;; SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Commercial

(ns token-taper.main-test
  (:require
   [clojure.test :refer [deftest is]]
   [token-taper.main :as main]))

(deftest main-vars-test
  (is (fn? main/start-api!))
  (is (fn? main/run-migrations!)))
