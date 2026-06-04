;; SPDX-FileCopyrightText: 2026 Haluan Irsad
;; SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Commercial

(ns token-taper.test-support.logging
  (:require
   [token-taper.observability.logging :as logging]))

(defn test-logger
  []
  (logging/test-context))

(defn capture-logs!
  [f]
  (binding [logging/*log-sink* (atom [])]
    (let [logger (test-logger)
          result (f logger)]
      {:result result
       :logger logger
       :entries (logging/parse-sink-lines @logging/*log-sink*)})))
