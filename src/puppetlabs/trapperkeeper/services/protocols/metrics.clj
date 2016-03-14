(ns puppetlabs.trapperkeeper.services.protocols.metrics)

(defprotocol MetricsService
  "A service that tracks runtime metrics for the process."
  (get-metrics-registry
    [this]
    [this domain]
    "Provides access to a MetricsRegistry where `domain` is the string used to
     look up the registry. Specifing no `domain` will return the default
     MetricsRegistry. The `domain` is the name that will appear at the front of
     the JMX metric. For example in `foo:name=my-metric`, `foo` is the
     `domain`."))
