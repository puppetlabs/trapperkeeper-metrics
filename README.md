# `trapperkeeper-metrics`

[![Build Status](https://travis-ci.org/puppetlabs/trapperkeeper-metrics.svg?branch=master)](https://travis-ci.org/puppetlabs/trapperkeeper-metrics)

[![Clojars Project](http://clojars.org/puppetlabs/trapperkeeper-metrics/latest-version.svg)](http://clojars.org/puppetlabs/trapperkeeper-metrics)

`trapperkeeper-metrics` is a library intended to help make it easier to track
metrics in other Trapperkeeper applications.  It includes:

 * a TK service that manages the life cycle of your metrics registry
 * config-driven control of metrics and metrics reporting
 * other miscellaneous utility functions for working with metrics
 * support for configuring metrics to be reported to Graphite
 * support for filtering metrics before reporting them to Graphite

For more detailed information (what this library does and doesn't do, more
detailed tips on how to write code against it, future plans, etc.), check out the
[documentation](./documentation/index.md).

See the [configuration docs](./documentation/configuration.md) for info
on how to configure the service, including Graphite reporting.

See the [api docs](./documentation/api.md) for service API documentation.

See the [metrics reporting and
filtering](./documentation/metrics_reporting_and_filtering.md) for information
on how metrics filtering works.

## Example code

One example, a Trapperkeeper service which uses the metrics service in conjunction with a Ring handler, is included with this project ([source code](./examples/ring_app/README.md)).

## HTTP Metrics with `comidi`

To get the most value out of this library, use it in concert with
[comidi](https://github.com/puppetlabs/comidi) and
[trapperkeeper-comidi-metrics](https://github.com/puppetlabs/trapperkeeper-comidi-metrics) (to take advantage of the
built-in HTTP metrics; see the trapperkeeper-comidi-metrics docs)
and the [Trapperkeeper Status Service](https://github.com/puppetlabs/trapperkeeper-status)
(to expose the most useful metrics data from your app via HTTP).

The `trapperkeeper-comidi-metrics` repo contains a working example app that illustrates how to tie everything together.

#Support

Please log tickets and issues at our [Jira Tracker](https://tickets.puppetlabs.com/issues/?jql=project%20%3D%20Trapperkeeper).


