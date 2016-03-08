(ns puppetlabs.trapperkeeper.services.metrics.metrics-service
  (:require [puppetlabs.trapperkeeper.core :as trapperkeeper]
            [puppetlabs.trapperkeeper.services.protocols.metrics :as metrics]
            [puppetlabs.trapperkeeper.services.metrics.metrics-core :as core]
            [puppetlabs.trapperkeeper.services :as tk-services]))

(trapperkeeper/defservice metrics-service
  metrics/MetricsService
  [[:ConfigService get-in-config]]

  (init [this context]
    {:registries
     (atom {::default-registry
            (core/initialize (get-in-config [:metrics] {})
                             nil)})})

  (stop [this context]
    (let [{:keys [registries] :as ctx} (tk-services/service-context this)]
      (doseq [[_ metrics-reg] @registries]
        (core/stop metrics-reg))
      ctx))

  (get-metrics-registry [this]
    (-> @(:registries (tk-services/service-context this))
        ::default-registry
        :registry))

  (get-metrics-registry [this registry-key domain]
    (:registry
      (core/get-or-initialize! (get-in-config [:metrics] {})
          (tk-services/service-context this)
          registry-key
          domain))))

(trapperkeeper/defservice metrics-webservice
  [[:ConfigService get-in-config]
   [:WebroutingService add-ring-handler get-route]]

  (init [this context]
        (when (get-in-config [:metrics :reporters :jmx :enabled] false)
          (add-ring-handler this
                            (core/build-handler (get-route this))))
        context)

  (stop [this context] context))

