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
}
```
