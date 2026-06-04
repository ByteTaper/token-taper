;; SPDX-FileCopyrightText: 2026 Haluan Irsad
;; SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Commercial

(ns token-taper.health.checks
  (:require
   [clojure.string :as str]))

(defn ok
  []
  {:status :ok})

(defn error
  ([reason]
   (error reason nil))
  ([reason message]
   (cond-> {:status :error :reason reason}
     message (assoc :message message))))

(defn unknown
  [reason]
  {:status :unknown :reason reason})

(defn ok?
  [{:keys [status]}]
  (= :ok status))

(defn error?
  [{:keys [status]}]
  (= :error status))

(defn unknown?
  [{:keys [status]}]
  (= :unknown status))

(defn- keyword->snake
  [k]
  (when k
    (str/replace (name k) #"-" "_")))

(defn ->json-check
  ([check]
   (->json-check check true))
  ([{:keys [status reason message] :as check} include-details?]
   (cond-> {:status (name status)}
     reason (assoc :reason (keyword->snake reason))
     (and include-details? message) (assoc :message message))))
