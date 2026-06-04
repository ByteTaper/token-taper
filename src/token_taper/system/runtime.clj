;; SPDX-FileCopyrightText: 2026 Haluan Irsad
;; SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Commercial

(ns token-taper.system.runtime)

(defn runtime-info
  []
  {:jvm (str (System/getProperty "java.specification.version"))
   :java_version (str (System/getProperty "java.version"))
   :clojure_version (clojure-version)})
