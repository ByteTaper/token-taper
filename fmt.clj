;; SPDX-FileCopyrightText: 2026 Haluan Irsad
;; SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Commercial

(ns fmt
  (:require
   [cljfmt.report :as report]
   [cljfmt.tool :as tool]))

(def fmt-opts
  {:paths ["src" "test" "build.clj"]
   :report report/clojure})

(defn fmt-check [_]
  (tool/check fmt-opts))

(defn fmt-fix [_]
  (tool/fix fmt-opts))
