(ns puppetlabs.trapperkeeper.services.metrics.metrics-service
  (:require [puppetlabs.trapperkeeper.core :as trapperkeeper]
            [puppetlabs.trapperkeeper.services.protocols.metrics :as metrics]
            [puppetlabs.trapperkeeper.services.metrics.metrics-core :as core]
            [puppetlabs.trapperkeeper.services :as tk-services]))

(trapperkeeper/defservice metrics-service
  metrics/MetricsService
  [[:ConfigService get-in-config]]
  (init [this context]
    (core/initialize (get-in-config [:metrics] {})))
  (stop [this context]
    (core/stop (tk-services/service-context this)))

  (get-metrics-registry
    [this]
    (:registry (tk-services/service-context this))))