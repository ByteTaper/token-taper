;; SPDX-FileCopyrightText: 2026 Haluan Irsad
;; SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Commercial

(ns token-taper.task.errors)

(defn validation-error
  [message & [{:keys [details]}]]
  (ex-info message
           {:error/kind :validation
            :error/message message
            :error/details details}))

(defn not-found-error
  [message & [{:keys [details]}]]
  (ex-info message
           {:error/kind :not-found
            :error/message message
            :error/details details}))

(defn conflict-error
  [message & [{:keys [details]}]]
  (ex-info message
           {:error/kind :conflict
            :error/message message
            :error/details details}))

(defn validation-error?
  [e]
  (= :validation (:error/kind (ex-data e))))

(defn not-found-error?
  [e]
  (= :not-found (:error/kind (ex-data e))))

(defn conflict-error?
  [e]
  (= :conflict (:error/kind (ex-data e))))

(defn unique-violation?
  [e]
  (= "23505" (get-in (ex-data e) [:jdbc/sql-state])))
