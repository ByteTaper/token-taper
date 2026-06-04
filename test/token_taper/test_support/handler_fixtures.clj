;; SPDX-FileCopyrightText: 2026 Haluan Irsad
;; SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Commercial

(ns token-taper.test-support.handler-fixtures
  (:require
   [token-taper.system.build :as build]
   [token-taper.system.info :as system-info]))

(def default-health-app
  {:service-name "token-taper"
   :service-version "0.1.0-SNAPSHOT"
   :environment "test"
   :status :started})

(defn system-info-payload
  ([app] (system-info-payload app {}))
  ([app _health-system]
   (let [{:keys [build-info]} (build/load-build-info!)]
     (system-info/build-for-app
      app
      (build/merge-overrides app build-info)
      :system-meta {:service "tokentaper" :name "TokenTaper"}))))

(defn default-health-system
  ([]
   (default-health-system nil))
  ([datasource]
   {:app default-health-app
    :config {:status :loaded}
    :datasource datasource
    :health-config {:database-timeout-ms 1000}}))
