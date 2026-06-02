;; SPDX-FileCopyrightText: 2026 Haluan Irsad
;; SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Commercial

(ns token-taper.system.integrant
  (:require
   [integrant.core :as ig]))

(defn prep-system
  [config]
  (ig/prep config))

(defn start-system!
  [config]
  (-> config
      prep-system
      ig/init))

(defn stop-system!
  [system]
  (when system
    (ig/halt! system)))
