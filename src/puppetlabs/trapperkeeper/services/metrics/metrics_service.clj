(ns puppetlabs.trapperkeeper.services.metrics.metrics-service
  (:require [puppetlabs.trapperkeeper.core :as trapperkeeper]
            [puppetlabs.trapperkeeper.services.protocols.metrics :as metrics]
            [puppetlabs.trapperkeeper.services.metrics.metrics-core :as core]
            [puppetlabs.trapperkeeper.services.metrics.jolokia :as jolokia]
            [puppetlabs.trapperkeeper.services :as tk-services]))

(trapperkeeper/defservice metrics-service
  metrics/MetricsService
  [[:ConfigService get-in-config]]

  (init [this context]
    {:registries
     (atom {"default"
            (core/initialize (get-in-config [:metrics] {})
                             nil)})})

  (stop [this context]
    (let [{:keys [registries] :as ctx} (tk-services/service-context this)]
      (doseq [[_ metrics-reg] @registries]
        (core/stop metrics-reg))
      ctx))

  (get-metrics-registry [this]
    (-> @(:registries (tk-services/service-context this))
        (get "default")
        :registry))

  (get-metrics-registry [this domain]
    (:registry
      (core/get-or-initialize! (get-in-config [:metrics] {})
          (tk-services/service-context this)
          domain)))

  (get-server-id [this]
    (get-in-config [:metrics :server-id]))

  (initialize-registry-settings [this domain settings]
   (throw (RuntimeException.
           "`initialize-registry-settings` is not yet implemented for this service"))))

(trapperkeeper/defservice metrics-webservice
  [[:ConfigService get-in-config]
   [:WebroutingService add-ring-handler get-route get-server]
   [:WebserverService add-servlet-handler]]

  (init [this context]
    (add-ring-handler this
                      (core/build-handler (get-route this)))

    (if (get-in-config [:metrics :metrics-webservice :jolokia :enabled] true)
      (let [config (->> (get-in-config [:metrics :metrics-webservice :jolokia :servlet-init-params] {})
                        jolokia/create-servlet-config)
            ;; NOTE: Normally, these route and server lookups would be done by
            ;; WebroutingService/add-servlet-handler, but that doesn't properly
            ;; mount sub-paths at the moment (TK-420). So we explictly compute
            ;; these items and use WebserverService/add-servlet-handler instead.
            route (str (get-route this) "/v2")
            server (get-server this)
            options (if (nil? server)
                      {:servlet-init-params config}
                      {:servlet-init-params config :server-id (keyword server)})]
        (add-servlet-handler
          (jolokia/create-servlet)
          route
          options)))

    context)

  (stop [this context] context))

