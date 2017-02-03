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
     `domain`.")

  (get-server-id
   [this]
   "Get the server-id from the `metrics` -> `server-id` part of the config.")

  (initialize-registry-settings
   [this domain settings]
   "Allows for specifying settings for a metric registry reporter that don't
   go into a config file. Must be called during the 'init' phase of a
   service's lifecycle. Will error if called more than once per metric
   registry."))
