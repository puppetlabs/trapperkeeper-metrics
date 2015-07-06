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
provides a Trapperkeeper service (`MetricsService`) that manages the life
cycle of a `MetricRegistry`.  The service includes a function, `get-registry`,
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

## What This Library Does *Not* Do

### Low-level HTTP API

This library does not (yet?) provide a low-level HTTP API for accessing individual
metrics.  There are a handful of reasons why we don't have support for this yet:

* In general, it seems like it will probably be more useful to have a pre-defined
  set of metrics that will be the most useful / meaningful for monitoring or
  debugging an application, and expose this set via a single HTTP endpoint (e.g.
  via the Trapperkeeper Status Service), as opposed to the user needing to be
  aware of the list of interesting metrics and request them individually.
* PuppetDB already contains an HTTP API for accessing individual metrics.  We
  wanted to avoid re-inventing that wheel; ideally we'll end up extracting that
  code from PuppetDB and moving it here, for cases when a low-level HTTP API for
  accessing individual metrics is important.
* Since we support sending the metrics data to JMX, there are several existing
  tools and approaches that can be used to read the data for individual metrics
  via JMX.  JVisualVM is one example; see
  [pe-puppetserver-jruby-jmx-client](https://github.com/puppetlabs/pe-puppetserver-jruby-jmx-client)
  for an example of how to do this from a JRuby script.

## Notes for Developers

For best results, use this library in combination with the
[`comidi`](https://github.com/puppetlabs/comidi) library,
and then take advantage of the `wrap-with-request-metrics` Ring middleware
to track metrics about all HTTP requests made to your application.  See
the `comidi` docs for more info.

## In The Future There Will Be Robots

Some ideas for things we might want to add/change in the future:

### Low-level HTTP API

Would very much like to bring over the PuppetDB low-level metrics API and include
it as another service available with this library.

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
