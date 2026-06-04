;; SPDX-FileCopyrightText: 2026 Haluan Irsad
;; SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Commercial

(ns token-taper.system.info-test
  (:require
   [clojure.test :refer [deftest is]]
   [token-taper.system.build :as build]
   [token-taper.system.info :as sysinfo]
   [token-taper.system.runtime :as runtime]
   [token-taper.test-support.system-info :as support]))

(def ^:private test-app
  {:service-version "0.1.0-SNAPSHOT"
   :environment "test"})

(deftest system-info-returns-service-identity-test
  (let [payload (sysinfo/build-for-app test-app build/build-fallbacks)]
    (is (= "tokentaper" (:service payload)))
    (is (= "TokenTaper" (:name payload)))
    (is (= "0.1.0-SNAPSHOT" (:version payload)))
    (is (= "test" (:environment payload)))))

(deftest system-info-returns-build-metadata-test
  (let [payload (sysinfo/build-for-app test-app
                                    {:version "1.2.3"
                                     :git_sha "abc123"
                                     :git_branch "main"
                                     :build_time "2026-06-04T10:15:30Z"})]
    (is (= "abc123" (get-in payload [:build :git_sha])))
    (is (= "main" (get-in payload [:build :git_branch])))
    (is (= "2026-06-04T10:15:30Z" (get-in payload [:build :build_time])))))

(deftest system-info-missing-build-metadata-falls-back-test
  (let [payload (sysinfo/assemble-system-info {:config test-app :build-info {}})]
    (is (= "unknown" (get-in payload [:build :git_sha])))
    (is (= "unknown" (get-in payload [:build :git_branch])))
    (is (= "unknown" (get-in payload [:build :build_time])))
    (is (= "0.1.0-SNAPSHOT" (:version payload)))))

(deftest runtime-info-returns-required-fields-test
  (let [rt (runtime/runtime-info)]
    (is (string? (:jvm rt)))
    (is (string? (:java_version rt)))
    (is (= (clojure-version) (:clojure_version rt)))))

(deftest system-info-excludes-forbidden-fields-test
  (let [payload (sysinfo/build-for-app test-app build/build-fallbacks)]
    (is (not (support/forbidden-fields-present? payload)))))

(deftest load-build-info-reads-classpath-resource-test
  (let [{:keys [build-info failed?]} (build/load-build-info!)]
    (is (false? failed?))
    (is (= "0.1.0-SNAPSHOT" (:version build-info)))
    (is (= "unknown" (:git_sha build-info)))))
