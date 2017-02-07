(ns puppetlabs.trapperkeeper.services.metrics.metrics-service
  (:require [puppetlabs.trapperkeeper.core :as trapperkeeper]
            [puppetlabs.trapperkeeper.services.protocols.metrics :as metrics]
            [puppetlabs.trapperkeeper.services.metrics.metrics-core :as core]
            [puppetlabs.trapperkeeper.services.metrics.jolokia :as jolokia]
            [puppetlabs.trapperkeeper.services :as tk-services]
            [clojure.tools.logging :as log]
            [schema.core :as schema]
            [puppetlabs.kitchensink.core :as ks]))

(trapperkeeper/defservice metrics-service
  metrics/MetricsService
  [[:ConfigService get-in-config]]

  (init
   [this context]
   (let [config (get-in-config [:metrics])
         metrics-config (if (nil? (get-in config [:reporters :jmx :enabled]))
                          config
                          (do
                            (log/warn "Enabling JMX globally is deprecated;"
                                      "JMX can only be enabled per-registry.")
                            (ks/dissoc-in config [:reporters :jmx :enabled])))]
     (schema/validate core/PEMetricsConfig metrics-config)
     (log/debug "Creating metrics registries")
     (core/create-initial-service-context metrics-config)))

  (start
   [this context]
   ;; registry settings can only be updated in the `init` phase of the lifecycle
   (let [updated-context (core/lock-registry-settings context)]
     (log/debug "Creating graphite reporters")
     (core/add-graphite-reporters updated-context)))

  (stop [this context]
    (core/stop-all (tk-services/service-context this)))

  (initialize-registry-settings
   [this domain settings]
   (let [context (tk-services/service-context this)]
     (core/initialize-registry-settings context domain settings)))

  (get-metrics-registry
   [this]
   (get-in @(:registries (tk-services/service-context this))
           [:default :registry]))

  (get-metrics-registry
   [this domain]
   (let [context (tk-services/service-context this)]
     (:registry (core/get-or-initialize-registry-context context domain))))

  (get-server-id
   [this]
   (get-in-config [:metrics :server-id])))

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

