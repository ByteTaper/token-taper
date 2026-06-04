;; SPDX-FileCopyrightText: 2026 Haluan Irsad
;; SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Commercial

(ns token-taper.system.info
  (:require
   [token-taper.system.runtime :as sys-runtime]))

(def ^:private service-id-default "tokentaper")
(def ^:private service-name-default "TokenTaper")

(defn- config-environment
  [config]
  (or (:environment config) "local"))

(defn assemble-system-info
  [{:keys [config build-info system-meta]}]
  (let [info-meta (or system-meta {})
        build-data (or build-info {})]
    {:service (or (:service info-meta) service-id-default)
     :name (or (:name info-meta) service-name-default)
     :version (or (:version build-data) "0.1.0-SNAPSHOT")
     :environment (config-environment config)
     :build {:git_sha (or (:git_sha build-data) "unknown")
             :git_branch (or (:git_branch build-data) "unknown")
             :build_time (or (:build_time build-data) "unknown")}
     :runtime (sys-runtime/runtime-info)}))

(defn build-for-app
  [app build-info & {:keys [system-meta]}]
  (assemble-system-info {:config app
                         :build-info build-info
                         :system-meta system-meta}))
