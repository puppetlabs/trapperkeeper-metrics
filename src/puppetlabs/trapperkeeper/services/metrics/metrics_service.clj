(ns puppetlabs.trapperkeeper.services.metrics.metrics-service
  (:require [puppetlabs.trapperkeeper.core :as trapperkeeper]
            [puppetlabs.jolokia :as jolokia]
            [puppetlabs.trapperkeeper.services.protocols.metrics :as metrics]
            [puppetlabs.trapperkeeper.services.metrics.metrics-core :as core]
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

  (initialize-registry-settings [this domain settings]
   (throw (RuntimeException.
           "`initialize-registry-settings` is not yet implemented for this service"))))

(trapperkeeper/defservice metrics-webservice
  [[:ConfigService get-in-config]
   [:WebroutingService add-ring-handler get-route]]

  (init [this context]
    (let [config (jolokia/create-config
                  {:agent-context (str (get-route this) "/v2")})
          logger (jolokia/create-logger)
          restrictor (jolokia/create-restrictor config logger)
          backend (jolokia/create-backend config logger restrictor)]
      (assoc context :jolokia-handler
             (jolokia/create-handler config backend logger))))

  (start [this context]
    (add-ring-handler this
                      (core/build-handler
                       (get-route this)
                       (:jolokia-handler context)))
    context))
