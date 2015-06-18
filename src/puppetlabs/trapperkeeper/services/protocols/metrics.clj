(ns puppetlabs.trapperkeeper.services.protocols.metrics)

(defprotocol MetricsService
  "A service that tracks runtime metrics for the process."
  (get-metrics-registry [this] "Provides access to the MetricsRegistry instance
                                where metrics are stored."))