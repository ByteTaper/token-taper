;; SPDX-FileCopyrightText: 2026 Haluan Irsad
;; SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Commercial

(ns token-taper.db.health
  (:require
   [next.jdbc :as jdbc]))

(defn check-ready
  [datasource]
  (try
    (jdbc/execute-one! datasource ["SELECT 1 AS ok"])
    {:status :ok
     :component :database
     :details {:query "SELECT 1"}}
    (catch Exception e
      {:status :error
       :component :database
       :error {:class (-> e class .getName)
               :message (ex-message e)}})))
