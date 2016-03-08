# `trapperkeeper-metrics` Documentation

## What This Library Does

The main purpose of this library is to provide some wrapper code around the
[Dropwizard Metrics Java Library](https://dropwizard.github.io/metrics/3.1.0/),
but there is some other functionality provided as well.  Here are the major features
available in `trapperkeeper-metrics`:

* A `Trapperkeeper` service that handles life cycle management for objects from
  the Dropwizard Metrics library
* Support for reading basic metrics configuration info from a `Trapperkeeper`
  config file, so that the metrics configuration syntax is consistent across
  all of your `Trapperkeeper` apps
* Other utility functions for creating and interacting with metrics in your
  application

For more detail on these features, read on.

### `MetricRegistry` Life Cycle

The main entry point into the Dropwizard Metrics API is a class called
[`MetricRegistry`](https://dropwizard.github.io/metrics/3.1.0/apidocs/com/codahale/metrics/MetricRegistry.html).

This class requires some basic initialization, so `trapperkeeper-metrics`
provides a Trapperkeeper service (`MetricsService`) that manages the life cycle
of a `MetricRegistry`. The service includes a function, `get-metrics-registry`,
so that all of your other Trapperkeeper services can access the registry and
register new metrics with it.

For example:

```clj
(defservice my-service
  [[:MetricsService get-metrics-registry]]
  (init [this context]
    (let [metrics-registry (get-metrics-registry)
          my-metric-name (metrics/host-metric-name "localhost" "my-metric")
          my-timer (.timer metrics-registry my-metric-name)]
      (metrics/time! my-timer (do-some-work)))
    context))
```

See the [source code for the sample app](https://github.com/puppetlabs/trapperkeeper-comidi-metrics/blob/master/dev/example/comidi_metrics_web_app.clj)
for a working example.  See the utility functions in the `trapperkeeper-metrics`
[`puppetlabs.metrics`](../src/puppetlabs/metrics.clj) namespace for some helpers
for constructing other kinds of metrics besides just `Timer`.  See the
[Dropwizard Metrics docs](https://dropwizard.github.io/metrics/3.1.0/) for more
info about all of the available metrics types and their features.

The `get-metrics-registry` also allows you to specify two additional fields,
`registry-key` and `domain` which allow you to create other registries (besides
the default given by `(get-metrics-registry)`) and allow you to configure the
namespace of the reporter for that registry.

```clj
(defservice my-service
  [[:MetricsService get-metrics-registry]]
  (init [this context]
    (let [default-metrics-registry (get-metrics-registry)
          my-metrics-registry (get-metrics-registry ::my-registry "my.metrics.domain")
          ;; This will create the metric
          ;; `my.metrics.domain:name=puppetlabs.localhost.my-metric`
          my-metric-name (metrics/host-metric-name "localhost" "my-metric")
          my-timer (.timer metrics-registry my-metric-name)]
      (assert (not= default-metrics-registry my-metrics-registry))
      (metrics/time! my-timer (do-some-work)))
    context)

  (start [this context]
    ;; We can retrieve the same metrics-registry later.
    (let [my-metrics-registry (get-metrics-registry ::my-registry "my.metrics.domain")]
      (do-some-other-work my-metrics-registry))
    context))
```

### Configuration & Reporters

The `MetricsService` also provides a configuration syntax that users can use to
configure the metrics for a running TK app.  This means that all TK services can
provide a consistent interface for interacting with metrics.

For more specific details, see the
[`MetricsService` configuration docs](../documentation/configuration.md).

### Utility Functions

The [`puppetlabs.metrics`](../src/puppetlabs/metrics.clj) namespace contains some
utility functions for working with metrics.  See the source code and docstrings
for more detail.  Here are a few bits of basic info:

`time!` is a macro that can be used to time some Clojure forms against an existing
`Timer`.  e.g.:

```clj
(let [my-timer (.timer (get-metrics-registry) "my.metric.name")]
    (time! my-timer
      (do-some-work!)
      (do-some-more-work!)))
```

`host-metric-name` can be used to provide a qualified, namespaced metric name.
For best results, it is advisable to use this function to create a name for each
of your application's metrics; this will ensure that the metrics are namespaced
consistently across services.  It will also ensure that metrics are namespaced
by hostname, which is critical when consolidating metrics data from multiple
hosts/services.  You can use the
`server-id` value from the `MetricsService` configuration to get the appropriate
hostname for your metric.

(TODO: this part of the API needs to be fleshed out a bit further.  We might want
to have a more dynamic way to get the hostname/server-id rather than having to
put it into the config file.  We may want to tweak some other things as well.
*In the interim*, it's probably a good idea to
*make it clear in your application/service documentation* that the specific
metric namespaces should not be considered part of a formal API and may change
in subsequent releases.)

`register` can be used to add a metric to an existing `MetricRegistry`.

`ratio`, `metered-ratio`, and `gauge` can be used to construct other types of
Metrics.

### Low-level HTTP API

To enable the HTTP API for accessing individual metrics add the service to your
`bootstrap.cfg` and configure the `web-router-service` accordingly:

```
web-router-service {
  "puppetlabs.trapperkeeper.services.metrics.metrics-service/metrics-webservice" : "/metrics"
}
```

#### Listing available metrics

##### Request format

To get a list of all available metric names:

* Request `/metrics/v1/mbeans`.
* Use a `GET` request.

##### Response format

Responses return a JSON object mapping a string to a string:

* The key is the name of a valid MBean.
* The value is a URI to use for requesting that MBean's attributes.

#### Retrieving a specific metric

##### Request format

To get the attributes of a particular metric:

* Request `/metrics/v1/mbeans/<name>`, where `<name>` is something that was
  returned in the list of available metrics specified above.
* Use a `GET` request.

##### Response format

Responses return a JSON object mapping strings to (strings/numbers/Booleans).

For example, using `curl` from localhost:

    curl 'http://localhost:8080/metrics/v1/mbeans/java.lang:type=Memory'
    {
      "ObjectPendingFinalizationCount" : 0,
      "HeapMemoryUsage" : {
        "committed" : 807403520,
        "init" : 268435456,
        "max" : 3817865216,
        "used" : 129257096
      },
      "NonHeapMemoryUsage" : {
        "committed" : 85590016,
        "init" : 24576000,
        "max" : 184549376,
        "used" : 85364904
      },
      "Verbose" : false,
      "ObjectName" : "java.lang:type=Memory"
    }

#### Alternatives

Since we support sending the metrics data to JMX, there are several existing
tools and approaches that can be used to read the data for individual metrics
via JMX. JVisualVM is one example; see
[pe-puppetserver-jruby-jmx-client](https://github.com/puppetlabs/pe-puppetserver-jruby-jmx-client)
for an example of how to do this from a JRuby script.

## What This Library Does *Not* Do

## Notes for Developers

For best results, use this library in combination with the
[`comidi`](https://github.com/puppetlabs/comidi) library,
and then take advantage of the `wrap-with-request-metrics` Ring middleware
to track metrics about all HTTP requests made to your application.  See
the `comidi` docs for more info.

## In The Future There Will Be Robots

Some ideas for things we might want to add/change in the future:

### metrics-clojure

There is an existing clojure library that wraps Dropwizard Metrics:
[`metrics-clojure`](https://github.com/sjl/metrics-clojure).  At the time
when we originally wrote our metrics code, this library was very out-of-date
and didn't support some of the features we needed.  It also seemed like a pretty
thin facade around the Java library, and we decided that it wasn't worth
adding an extra dependency.

Since then, it's been updated and should be much more compatible with the
more recent versions of Dropwizard Metrics.  At a glance, it seems like it
would be possible for other TK services to use `metrics-clojure` in
combination with `trapperkeeper-metrics` as-is; it mostly just provides utility
functions that should work fine with the `MetricRegistry` object surfaced
by `trapperkeeper-metrics`.  So, if you feel like the abstractions it
provides over the Java library are worthwhile, try it out and let us know if
something doesn't work properly.

At some point in the future we may go ahead and add it as a direct dependency
and refactor things in `trapperkeeper-metrics` to use it, but so far there
hasn't been a hugely compelling reason to do so.

### Additional Facilities for Exposing Metrics

There are a few other ideas floating around for how to make it easier for
apps/services that are using `trapperkeeper-metrics` to expose the metrics info
to end users.  More utility functions for easier integration with status service,
other tools for facilitating consumption / visualization / etc.  Stay tuned.
