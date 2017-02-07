(ns puppetlabs.enterprise.services.metrics.pe-metrics-service
  (:require [puppetlabs.trapperkeeper.core :as trapperkeeper]
            [puppetlabs.trapperkeeper.services.protocols.metrics :as metrics]
            [puppetlabs.enterprise.services.metrics.pe-metrics-core :as core]
            [puppetlabs.trapperkeeper.services :as tk-services]
            [puppetlabs.kitchensink.core :as ks]
            [clojure.tools.logging :as log]
            [schema.core :as schema]))

(trapperkeeper/defservice pe-metrics-service
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
