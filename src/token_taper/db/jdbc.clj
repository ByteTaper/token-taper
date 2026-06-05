;; SPDX-FileCopyrightText: 2026 Haluan Irsad
;; SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Commercial

(ns token-taper.db.jdbc
  (:require
   [next.jdbc :as jdbc]
   [next.jdbc.result-set :as jdbc-rs]))

(def ^:private default-opts
  {:builder-fn jdbc-rs/as-unqualified-kebab-maps})

(defn execute!
  [connectable sql-params]
  (if (vector? sql-params)
    (jdbc/execute! connectable sql-params default-opts)
    (jdbc/execute! connectable sql-params default-opts)))

(defn execute-one!
  [connectable sql-params]
  (if (vector? sql-params)
    (jdbc/execute-one! connectable sql-params default-opts)
    (jdbc/execute-one! connectable sql-params default-opts)))

(defmacro with-transaction
  [[tx connectable opts] & body]
  `(jdbc/with-transaction [~tx ~connectable ~opts]
     ~@body))
