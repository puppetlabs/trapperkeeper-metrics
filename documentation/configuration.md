# Configuring the `MetricsService`

All metrics get registered into a Dropwizard `MetricsRegistry`. Typically, each
TK application will have its own metric registry, and multiple TK services
will share that registry. The Metrics service has support for having multiple
metrics registries. Each registry has its own `domain`, so that if multiple
applications with different registries are running together in the same
process, they will not have conflicts. There is also a `default` metrics
registry that is automatically created.

Much of the Metrics service configuration is per-registry. However, the
`server-id` settings and the `reporters` settings (for `host`, `port`, and
`update-interval-seconds`) are global (across all registries).

To configure the Metrics service, edit the `metrics.conf` file in your
`conf.d` directory.

#### `metrics.conf`

Here is a sample config file that illustrates the available settings for metrics:

```
metrics: {
    # a server id that will be used as part of the namespace for metrics produced
    # by this server
    server-id: localhost

    registries: {
        my-application: {

            # specify metrics to include in addition to the default list provided.
            # The combination of these two lists will be what gets exported to graphite and
            # other reporters (excluding JMX).
            metrics-allowed: [
                "foo.bar"
            ]

            # the default prefix prepended to metric names on the
            # `metrics-allowed` list before filtering them is `puppetlabs.<server-id>`. If
            # this does not match the prefix of an application's metrics, specify a different
            # prefix here so that metrics will be correctly filtered.
            metrics-prefix: "my-metrics"

            # enable and disable specific reporters, and set configuration
            # options specific for the registry.
            reporters: {

                # enable or disable JMX metrics reporter
                jmx: {
                    enabled: true
                }

                # enable or disable graphite metrics reporter for this registry
                # and optionally override host/port/update-interval.
                graphite: {
                    enabled: true
                    update-interval-seconds: 5
                }
            }
        }
    }

    # this section is used to configure host, port, and update-interval-seconds
    # for reporters. Note that reporters are not enabled in this section -
    # they must be enabled per-registry.
    reporters: {
        graphite: {
            # graphite host
            host: "127.0.0.1"
            # graphite metrics port
            port: 2003
            # how often to send metrics to graphite
            update-interval-seconds: 60
        }
    }
    
    metrics-webservice: {
        jolokia: {
            # Enable or disable the Jolokia-based metrics/v2 endpoint.
            # Default is true.
            enabled: false

            # Configure any of the settings listed at:
            #   https://jolokia.org/reference/html/agents.html#war-agent-installation
            servlet-init-params: {
                # Specify a custom security policy:
                #  https://jolokia.org/reference/html/security.html
                policyLocation: "file:///etc/puppetlabs/<service name>/jolokia-access.xml"
            }
        }
    }
}
```
