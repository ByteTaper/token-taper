;; SPDX-FileCopyrightText: 2026 Haluan Irsad
;; SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Commercial

(ns token-taper.test-support.system-info
  (:require
   [clojure.string :as str]))

(def forbidden-field-names
  #{"database_url"
    "jdbc_url"
    "db_url"
    "password"
    "secret"
    "token"
    "api_key"
    "authorization"
    "cookie"
    "private_key"
    "access_key"})

(defn- normalize-key-name
  [k]
  (str/lower-case (name k)))

(defn collect-key-names
  [value]
  (cond
    (map? value)
    (into #{}
          (mapcat (fn [[k v]]
                    (into #{(normalize-key-name k)}
                          (collect-key-names v)))
                  value))

    (coll? value)
    (into #{} (mapcat collect-key-names value))

    :else
    #{}))

(defn forbidden-fields-present?
  [value]
  (not (empty? (clojure.set/intersection forbidden-field-names (collect-key-names value)))))
