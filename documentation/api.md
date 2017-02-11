# `MetricsService` API

The `MetricsService` protocol provides two functions: `get-metrics-registry`
and `initialize-registry-settings`.

### `get-metrics-registry`

Takes an optional registry domain as a keyword. Returns the Dropwizard
`MetricRegistry` for that domain, which can then be used to register new
metrics. If no registry for this domain previously existed, creates the metric
registry. If no domain is provided, returns the default registry.

Can be called at any time, regardless of if or when
`initalize-registry-settings` has been called.

### `initialize-registry-settings`

Takes a domain and a map of settings, and registers these settings for the
domain. Currently, the only available setting is `default-metrics-allowed`,
which takes a vector of strings.

This function can only be called once per domain. This means if there is more
than one Trapperkeeper service registering metrics with the same registry,
only one service can call `initialize-registry-settings`. Either designate a
service to have this responsibility, or create a separate service to handle
metrics-related things.

Must be called during the `init` phase of the service lifecycle, since these
settings are used to initialize registries and reporters during the metrics
service's `start` phase.

Example usage:

```clojure
(trapperkeeper/defservice my-service
  [[:MetricsService get-metrics-registry initialize-registry-settings]]
  (init
   [this context]
   (initialize-registry-settings :foo {:default-metrics-allowed ["foo" "foo.bar"])
   ...)
  (start
   [this context]
   (let [registry (get-metrics-registry :foo)]
     (core/do-something-with-metrics registry))
   ...))
```

## Registry settings

### `default-metrics-allowed`

This setting allows for specifying a list of metric names (as strings) that
will be exported to configured reporters (e.g. Graphite - note that this
setting does not apply to JMX). See [Metrics
Filtering](./metrics_reporting_and_filtering.md) for more info.
