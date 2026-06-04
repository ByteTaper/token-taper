;; SPDX-FileCopyrightText: 2026 Haluan Irsad
;; SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Commercial

(ns token-taper.system.build
  (:require
   [aero.core :as aero]
   [clojure.java.io :as io]
   [clojure.string :as str]))

(def build-info-resource "build-info.edn")

(def build-fallbacks
  {:version "0.1.0-SNAPSHOT"
   :git_sha "unknown"
   :git_branch "unknown"
   :build_time "unknown"})

(defn- keywordize-build-keys
  [m]
  {:version (or (:version m) "0.1.0-SNAPSHOT")
   :git_sha (or (:git_sha m) (:git-sha m) "unknown")
   :git_branch (or (:git_branch m) (:git-branch m) "unknown")
   :build_time (or (:build_time m) (:build-time m) "unknown")})

(defn load-build-info!
  "Loads build-info.edn from the classpath. On failure returns defaults and
  metadata for structured logging (caller emits build_info_load_failed)."
  []
  (try
    (if-let [resource (io/resource build-info-resource)]
      {:build-info (keywordize-build-keys (aero/read-config resource))
       :failed? false
       :source (str "classpath:" build-info-resource)}
      (throw (ex-info "Build info resource not found"
                      {:resource build-info-resource})))
    (catch Throwable t
      {:build-info build-fallbacks
       :failed? true
       :source (str "classpath:" build-info-resource)
       :throwable t})))

(defn merge-overrides
  [app build-info]
  (let [git-sha (System/getenv "TOKEN_TAPER_GIT_SHA")]
    (cond-> build-info
      (:service-version app) (assoc :version (:service-version app))
      (and git-sha (not (str/blank? git-sha))) (assoc :git_sha git-sha))))
