;; SPDX-FileCopyrightText: 2026 Haluan Irsad
;; SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Commercial

(ns token-taper.observability.logging
  (:require
   [clojure.string :as str]
   [jsonista.core :as json]
   [token-taper.observability.route :as route])
  (:import
   [java.time Instant]
   [java.time.format DateTimeFormatter]))

(def ^:dynamic *log-sink* nil)

(def default-redact-fields
  #{"authorization"
    "api_key"
    "x-api-key"
    "password"
    "secret"
    "token"
    "database_url"
    "jdbc_url"
    "cookie"
    "set-cookie"})

(def ^:private level-priority
  {:debug 10
   :info 20
   :warn 30
   :error 40
   :fatal 50})

(def ^:private json-mapper
  (json/object-mapper {:encode-key-fn name}))

(def ^:private iso-formatter
  DateTimeFormatter/ISO_INSTANT)

(defn iso-timestamp
  []
  (.format iso-formatter (Instant/now)))

(defn- normalize-key
  [k]
  (cond
    (keyword? k) (name k)
    (symbol? k) (name k)
    (string? k) (str/lower-case k)
    :else (str/lower-case (str k))))

(defn- redact-key?
  [k redact-fields]
  (contains? (into #{} (map normalize-key redact-fields))
             (normalize-key k)))

(defn redact-value
  [k v redact-fields]
  (if (redact-key? k redact-fields)
    "[REDACTED]"
    v))

(defn redact-map
  ([m redact-fields]
   (redact-map m redact-fields #{"body" "request-body" "response-body"}))
  ([m redact-fields exclude-keys]
   (into {}
         (keep (fn [[k v]]
                 (let [nk (normalize-key k)]
                   (cond
                     (contains? exclude-keys nk) nil
                     (map? v) [k (redact-map v redact-fields exclude-keys)]
                     :else [k (redact-value k v redact-fields)])))
               m))))

(defn- level-enabled?
  [ctx level]
  (<= (get level-priority (:level ctx :info) 20)
      (get level-priority level 20)))

(defn build-event
  [ctx level event extra]
  (merge {:timestamp (iso-timestamp)
          :level (name level)
          :event (name event)
          :service (:service ctx "tokentaper")
          :version (:version ctx "unknown")
          :env (:env ctx "unknown")}
         (redact-map (or extra {}) (:redact-fields ctx default-redact-fields))))

(defn build-error-fields
  [ctx throwable]
  (cond-> {:error_class (.getName (class throwable))
           :error_message (.getMessage throwable)}
    (and throwable (:include-stacktrace? ctx))
    (assoc :stacktrace (str/trim (with-out-str (.printStackTrace ^Throwable throwable))))))

(defn- header-value
  [request k]
  (let [headers (:headers request)]
    (or (get headers k)
        (get headers (str/lower-case k))
        (some (fn [[hk hv]]
                (when (= (normalize-key hk) (normalize-key k))
                  hv))
              headers))))

(defn build-request-log-event
  [_ctx request response duration-ms]
  (let [status (or (:status response) 500)]
    {:request_id (:request-id request)
     :method (some-> (:request-method request) name str/upper-case)
     :path (:uri request)
     :route (route/route-label request)
     :status status
     :duration_ms duration-ms
     :remote_addr (:remote-addr request)
     :user_agent (header-value request "user-agent")}))

(defn level-for-status
  [status]
  (if (>= (long status) 500) :error :info))

(defn- encode-line
  [event-map]
  (json/write-value-as-string event-map json-mapper))

(defn emit!
  [ctx level event extra]
  (when (and ctx (level-enabled? ctx level))
    (let [line (str (encode-line (build-event ctx level event extra)) "\n")]
      (if *log-sink*
        (swap! *log-sink* conj line)
        (binding [*out* *err*]
          (.write ^java.io.Writer *out* line)
          (.flush ^java.io.Writer *out*))))))

(defn log!
  [ctx level event extra]
  (emit! ctx level event extra))

(defn debug!
  [ctx event extra]
  (log! ctx :debug event extra))

(defn info!
  [ctx event extra]
  (log! ctx :info event extra))

(defn warn!
  [ctx event extra]
  (log! ctx :warn event extra))

(defn error!
  [ctx event extra]
  (log! ctx :error event extra))

(defn fatal!
  [ctx event extra]
  (log! ctx :fatal event extra))

(defn parse-level
  [level]
  (cond
    (keyword? level) level
    (string? level) (keyword (str/lower-case level))
    :else :info))

(defn create-context
  [{:keys [app logging-config]}]
  (let [cfg (or logging-config {})
        env-level (or (System/getenv "TOKEN_TAPER_LOG_LEVEL")
                      (System/getenv "TOKENTAPER_LOG_LEVEL"))]
    {:service "tokentaper"
     :version (:service-version app "unknown")
     :env (:environment app "unknown")
     :level (parse-level (or env-level (:level cfg) :info))
     :format (keyword (or (System/getenv "TOKEN_TAPER_LOG_FORMAT") (:format cfg) :json))
     :include-stacktrace? (get cfg :include-stacktrace? true)
     :redact-fields (into default-redact-fields (map normalize-key (:redact-fields cfg)))}))

(defn bootstrap-context
  []
  (create-context {:app {:service-version (System/getenv "TOKEN_TAPER_VERSION")
                         :environment (or (System/getenv "TOKEN_TAPER_ENV") "unknown")}
                   :logging-config {}}))

(defn with-test-sink!
  [f]
  (binding [*log-sink* (atom [])]
    (f)))

(defn parse-sink-lines
  [lines]
  (mapv #(json/read-value (str/trim %) (json/object-mapper {:decode-key-fn keyword}))
        lines))

(defn test-context
  []
  (create-context {:app {:service-version "0.1.0-SNAPSHOT"
                         :environment "test"}
                   :logging-config {:level :debug
                                    :include-stacktrace? false}}))
