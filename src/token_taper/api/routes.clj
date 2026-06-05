;; SPDX-FileCopyrightText: 2026 Haluan Irsad
;; SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Commercial

(ns token-taper.api.routes
  (:require
   [reitit.ring :as ring]
   [token-taper.api.response :as response]
   [token-taper.health.service :as health]
   [token-taper.observability.metrics :as metrics]
   [token-taper.task.api :as task-api]))

(defn system-info-handler
  [system-info]
  (fn [_request]
    (response/diagnostic-ok system-info)))

(defn live-handler
  [health-system]
  (fn [_request]
    (let [{:keys [body]} (health/live health-system)]
      (response/health-ok body))))

(defn ready-handler
  [health-system]
  (fn [_request]
    (let [{:keys [http-status body]} (health/ready health-system)]
      (if (= 200 http-status)
        (response/health-ok body)
        (response/health-not-ready body)))))

(defn metrics-handler
  [metrics-component]
  (fn [_request]
    (metrics/scrape metrics-component)))

(defn router
  [{:keys [system-info health-system metrics datasource logger]}]
  (let [task-opts {:datasource datasource :logger logger :metrics metrics}]
    (ring/router
     [["/health/live" {:get (live-handler health-system)}]
      ["/health/ready" {:get (ready-handler health-system)}]
      ["/metrics" {:get (metrics-handler metrics)}]
      ["/v1/system/info" {:get (system-info-handler system-info)}]
      ["/v1/tasks/start" {:post (task-api/start-task-handler task-opts)}]
      ["/v1/tasks/:task_id" {:get (task-api/get-task-handler task-opts)}]
      ["/v1/tasks/:task_id/trace" {:get (task-api/get-task-trace-handler task-opts)}]
      ["/v1/tasks/:task_id/finish" {:post (task-api/finish-task-handler task-opts)}]]
     ;; POST /start vs GET /:task_id share a path template; methods differ at runtime.
     {:conflicts :ignore})))
