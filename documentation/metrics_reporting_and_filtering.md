# Metrics Reporting and Filtering

## Metrics Reporting

Currently, trapperkeeper-metrics has the ability to export metrics to two
metrics reporters: JMX and Graphite.

In order to set up metrics reporting, all that must be done is for the metrics
registry domain to be listed under the `registries` key, and for the desired
reporters to be enabled under that. In addition, the Graphite `host`, `port`,
and `update-interval-seconds` must be set if reporting to Graphite.

## Metrics Filtering

Some applications may have many more metrics than Graphite or other reporters
can handle, or than users might find useful to see in Graphite. For this
purpose, trapperkeeper-metrics provides the ability to filter the metrics
an application has registered before sending them to configured reporters.

If no filter is provided, all metrics are allowed through.

If a filter is provided, then only the metrics on the `allow` list are allowed
through.

The `allow` list is made up of 1) the metrics on the `default-metrics-allowed`
list, and 2) the metric names in the `metrics-allowed` section of the config.

TK application developers who want to have a list of metric names that users
cannot change (and don't have to worry about and will always be present after
upgrade) should set these with the `default-metrics-allowed` setting. This is
done using the [`update-registry-settings` service
function](./api.md#update-registry-settings).

The `metrics-allowed` setting in the config allows users to add additional
metrics (on top of the `default-metrics-allowed`) that they would like to have
exported. this is especially useful for metric names that include information
from their environment - e.g. a node name.

The metric names from the two lists (`default-metrics-allowed` and
`metrics-allowed`) are combined and then prefixed with either
`puppetlabs.<server-id>` (where `server-id` is taken from that setting in the
config) or the value of the `metrics-prefix` from the config. So, for
example, a metric that is named `foo.bar` and prefixed with
`puppetlabs.<server-id>` to be `puppetlabs.<server-id>.foo.bar` could be
"allowed" by adding it to the `default-metrics-allowed` list or the
`metrics-allowed` config setting as `foo.bar`. If it was instead
`some.other.prefix.foo.bar` then in addition to having `foo.bar` added to one
of the allowed metrics lists, the `metrics-prefix` would need to be set to
`some.other.metrics.prefix` in the config.

Note that this filtering does not apply to JMX - if JMX reporting is enabled,
all metrics will be sent to it, regardless of whether they are in the
`metrics-allowed` list or not.

In addition to metric name filtering, not all the metric "fields" are reported
to Graphite for histogram/timer metrics. For these metrics, the `min`, `max`,
`mean`, `count`, `stddev`, `p50`, `p75`, and `p95` are reported (`p98`, `p99`,
`p9999`, `m5rate`, `m10rate`, and `m15rate` are not reported). This is not
configurable.
