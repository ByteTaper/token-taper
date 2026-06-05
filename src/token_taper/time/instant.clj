;; SPDX-FileCopyrightText: 2026 Haluan Irsad
;; SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Commercial

(ns token-taper.time.instant
  "Coerce wire/domain timestamps to java.time.Instant before JDBC writes."
  (:require
   [token-taper.task.errors :as errors])
  (:import
   [java.sql Timestamp]
   [java.time Instant]))

(defn- validation-detail
  [field reason]
  {:field field :reason reason})

(defn parse-instant
  "Return an Instant for supported values, or nil when value is nil."
  [value]
  (cond
    (nil? value) nil
    (instance? Instant value) value
    (string? value) (Instant/parse value)
    (instance? java.time.OffsetDateTime value)
    (.toInstant ^java.time.OffsetDateTime value)
    (instance? java.time.LocalDateTime value)
    (.toInstant (.atZone ^java.time.LocalDateTime value java.time.ZoneOffset/UTC))
    (instance? Timestamp value) (.toInstant ^Timestamp value)
    :else nil))

(defn coerce-instant-field!
  "Parse optional timestamp field; throw validation-error when present but invalid."
  [field value message]
  (when (some? value)
    (try
      (if-let [parsed (parse-instant value)]
        parsed
        (throw (errors/validation-error
                message
                {:details [(validation-detail field "invalid")]})))
      (catch java.time.format.DateTimeParseException _
        (throw (errors/validation-error
                message
                {:details [(validation-detail field "invalid")]}))))))

(defn require-instant-field!
  "Require a non-nil Instant; throw validation-error when missing or invalid."
  [field value message]
  (if-let [parsed (coerce-instant-field! field value message)]
    parsed
    (throw (errors/validation-error
            message
            {:details [(validation-detail field "missing")]}))))

(defn instant->sql-timestamp
  "Convert a coerced Instant (or nil) to java.sql.Timestamp for JDBC."
  [value]
  (when value
    (if (instance? Timestamp value)
      value
      (Timestamp/from ^Instant value))))
