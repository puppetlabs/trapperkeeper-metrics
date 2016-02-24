(ns puppetlabs.trapperkeeper.services.protocols.metrics)

(defprotocol MetricsService
  "A service that tracks runtime metrics for the process."
  (get-metrics-registry
    [this]
    [this registry-key domain]
    "Provides access to a MetricsRegistry configured with ops where `registry`
     is the keyword used to look up the registry. Specifing no `registry` will
     return the default MetricsRegistry. The `domain` is the name that will
     appear at the front of the JMX metric. For example in `foo:name=my-metric`,
     `foo` is the `domain`."))
