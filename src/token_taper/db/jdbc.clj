;; SPDX-FileCopyrightText: 2026 Haluan Irsad
;; SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Commercial

(ns token-taper.db.jdbc
  (:require
   [next.jdbc :as jdbc]))

(defn execute!
  [connectable sql-params]
  (jdbc/execute! connectable sql-params))

(defn execute-one!
  [connectable sql-params]
  (jdbc/execute-one! connectable sql-params))

(defmacro with-transaction
  [[tx connectable opts] & body]
  `(jdbc/with-transaction [~tx ~connectable ~opts]
     ~@body))
