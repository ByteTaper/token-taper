;; SPDX-FileCopyrightText: 2026 Haluan Irsad
;; SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Commercial

(ns token-taper.api.server
  (:require
   [ring.adapter.jetty :as jetty]
   [reitit.ring :as ring]
   [token-taper.api.middleware :as middleware]
   [token-taper.api.response :as response]
   [token-taper.api.routes :as routes]
   [token-taper.observability.http-metrics :as http-metrics])
  (:import
   (org.eclipse.jetty.server Server Connector)))

(defn not-found-handler
  [request]
  (response/not-found {:request-id (:request-id request)
                       :message "Route not found"}))

(defn method-not-allowed-handler
  [request]
  (response/method-not-allowed {:request-id (:request-id request)
                                :message "Method not allowed"}))

(defn handler
  [{:keys [metrics logger] :as opts}]
  (let [ring-handler (ring/ring-handler
                      (routes/router opts)
                      (ring/routes
                       (ring/create-default-handler
                        {:not-found not-found-handler
                         :method-not-allowed method-not-allowed-handler})))]
    (-> ring-handler
        middleware/wrap-basic-headers
        (cond-> metrics (http-metrics/wrap-http-metrics metrics))
        (middleware/wrap-request-logging logger)
        (middleware/wrap-exception logger)
        middleware/wrap-request-id)))

(defn- bound-port
  [^Server server]
  (let [^Connector connector (first (.getConnectors server))]
    (.getLocalPort connector)))

(defn start-server!
  [{:keys [host port join?] :as http-config} app-opts]
  (let [server (jetty/run-jetty
                (handler app-opts)
                {:host host
                 :port port
                 :join? (boolean join?)})]
    {:server server
     :port (bound-port server)
     :config http-config}))

(defn stop-server!
  [{:keys [server config]}]
  (when server
    (let [^Server jetty-server server
          stop-timeout-ms (:stop-timeout-ms config)]
      (when stop-timeout-ms
        (.setStopTimeout jetty-server (long stop-timeout-ms)))
      (.stop jetty-server))))
