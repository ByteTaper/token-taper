;; SPDX-FileCopyrightText: 2026 Haluan Irsad
;; SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Commercial

(ns token-taper.observability.route)

(def known-routes
  #{"/health/live"
    "/health/ready"
    "/metrics"
    "/v1/system/info"
    "/v1/tasks/start"
    "/v1/tasks/:task_id"
    "/v1/tasks/:task_id/trace"
    "/v1/tasks/:task_id/finish"
    "/v1/events/llm-call"})

(defn route-label
  [request]
  (or (get-in request [:reitit.core/match :template])
      (known-routes (:uri request))
      "unknown"))
