;; SPDX-FileCopyrightText: 2026 Haluan Irsad
;; SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Commercial

(ns token-taper.api.system-info)

(defn build
  [{:keys [service-name service-version environment]}]
  {:service service-name
   :version service-version
   :environment environment
   :runtime {:jvm (System/getProperty "java.version")
             :clojure (str (:major *clojure-version*) "."
                           (:minor *clojure-version*) "."
                           (:incremental *clojure-version*))}})
