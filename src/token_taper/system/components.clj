;; SPDX-FileCopyrightText: 2026 Haluan Irsad
;; SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Commercial

(ns token-taper.system.components
  (:require
   [integrant.core :as ig]))

(defmethod ig/init-key :token-taper/app
  [_ config]
  (assoc config :status :started))

(defmethod ig/halt-key! :token-taper/app
  [_ _]
  nil)

(defmethod ig/init-key :token-taper/http
  [_ config]
  (assoc config :status :configured))

(defmethod ig/halt-key! :token-taper/http
  [_ _]
  nil)

(defmethod ig/init-key :token-taper/config
  [_ config]
  (assoc config :status :loaded))

(defmethod ig/halt-key! :token-taper/config
  [_ _]
  nil)
