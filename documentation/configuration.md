# Configuring the `MetricsService`

To configure the Metrics service, edit the `metrics.conf` file in your
`conf.d` directory.

#### `metrics.conf`

Here is a sample config file that illustrates the available settings for metrics:

```
metrics: {
    # a server id that will be used as part of the namespace for metrics produced
    # by this server
    server-id: localhost

    # this section is used to enable/disable JMX reporting
    reporters: {

        # enable or disable JMX metrics reporter
        jmx: {
            enabled: true
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
