;; SPDX-FileCopyrightText: 2026 Haluan Irsad
;; SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Commercial

(ns token-taper.api.error
  (:require
   [token-taper.observability.logging :as logging]
   [token-taper.observability.route :as route]))

(defn log-unhandled-exception!
  [logger request throwable]
  (when logger
    (logging/error!
     logger
     :unhandled_exception
     (merge {:request_id (:request-id request)
             :method (some-> (:request-method request) name)
             :path (:uri request)
             :route (route/route-label request)}
            (logging/build-error-fields logger throwable)))))
