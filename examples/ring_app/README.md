# Simple Ring App with Metrics Example

This example demonstrates how to incorporate the trapperkeeper-metrics
service into a simple Ring app.  This is based loosely upon the
[ring_app example] (https://github.com/puppetlabs/trapperkeeper-webserver-jetty10/tree/master/examples/ring_app)
in the trapperkeeper-webserver-jetty10 project.  See that example for more
information on the use of the `jetty10-service` and the Jetty web server
integration with Ring.

All code needed to execute this example is located in `./src/examples/ring_app`.
The Clojure code is contained in the `ring_app.clj` file.

This example configures a metrics registry for the count service and adds two
counters to that registry, `count-service-report-me` and
`count-service-dont-report-me`.  During initialization, the service only
includes the `count-service-report-me` counter but not the `count-service-dont-report-me`
counter in its list of `:default-metrics-allowed` for the count service registry:

~~~~clj
(update-registry-settings :count-service
                          {:default-metrics-allowed ["count-service-report-me"]})
~~~~

Metric info for the `count-service-report-me`
counter would be periodically reported to a Graphite server when the Graphite
reporter is enabled.  Info for the `count-service-dont-report-me` counter,
however, would not be reported to the Graphite server -- whether or not the
Graphite reporter is enabled.

The JMX reporter is enabled by default in the
`ring-example.conf` file.  Both the `count-service-report-me` and
`count-service-dont-report-me` counters are available via JMX, through the
`/metrics` webservice endpoint.

## Launching trapperkeeper and running the app

To start up trapperkeeper and launch the sample application, use the
following `lein` command while in the root of the trapperkeeper-metrics repo:

~~~~sh
lein trampoline run --config ./examples/ring_app/ring-example.conf \
                    --bootstrap-config ./examples/ring_app/bootstrap.cfg
~~~~

For convenience, the application could also be run instead via the
`ring-example` alias:

~~~~sh
lein ring-example
~~~~

### Running the app from the repl

To startup the sample application from the repl, use the following `lein`
command while in the root of the trapperkeeper-metrics repo:

~~~~sh
lein repl
~~~~

The `repl` prompt should display the namespace of the ring-app example.  Type `(go)` and press enter in order to launch the app:

~~~~sh
examples.ring-app.repl=> (go)
~~~~

### The `bootstrap.cfg` file

The bootstrap config file contains a list of services that trapperkeeper will
load up and make available.  They are listed as fully-qualified Clojure
namespaces and service names. For this example, the bootstrap.cfg looks like
this:

~~~~
puppetlabs.trapperkeeper.services.metrics.metrics-service/metrics-webservice
puppetlabs.trapperkeeper.services.metrics.metrics-service/metrics-service
puppetlabs.trapperkeeper.services.webserver.jetty10-service/jetty10-service
puppetlabs.trapperkeeper.services.webrouting.webrouting-service/webrouting-service
examples.ring-app.ring-app/count-service
~~~~

This configuration indicates that the metrics services, `WebserverService` and
`WebroutingService` from the `trapperkeeper-webserver-jetty10` project, and the
count service, defined in the `ring_app.clj` file, are to be loaded.

### The `ring-example.conf` configuration file

For the application configuration, a file called `ring-example.conf` provides
a fairly minimal configuration for the count service:

~~~~hocon
global: {
    logging-config: ./examples/ring_app/logback.xml
}

webserver: {
    port: 8080
}

web-router-service: {
    "puppetlabs.trapperkeeper.services.metrics.metrics-service/metrics-webservice": "/metrics"
}

metrics: {
    server-id: "localhost"

    registries: {
        count-service: {
            reporters: {
                jmx: {
                    enabled: true
                }
                graphite: {
                    enabled: false
                }
            }
        }
    }

    metrics-webservice: {
        jolokia: {
            enabled: true
        }
    }

    reporters: {
        graphite: {
            host: "127.0.0.1"
            port: 2003
            update-interval-seconds: 5
        }
    }
}
~~~~

The Graphite reporter is disabled by default in the `ring-example.conf`
configuration file.  Assuming a Graphite server were running on host `foo`,
you could make the following changes to the configuration to enable reporting
to the `foo` server:

~~~
...
metrics: {
    ...

    registries: {
        count-service: {
            reporters: {
                ...
                graphite: {
                    enabled: true
                }
            }
        }
    }

    ...

    reporters: {
        graphite: {
            host: "foo"
            ...
        }
    }
~~~

### Testing the counter service metrics

The counter service increments the `count-service-report-me` and
`count-service-dont-report-me` counters each time a request is made to its
`/count` endpoint.  To view the latest value stored in JMX for each counter,
query the `/metrics` webservice.

For example, the following request would obtain info for the
`count-service-report-me` counter:

~~~~sh
curl http://127.0.0.1:8080/metrics/v2/read/count-service:name=puppetlabs.localhost.count-service-report-me
~~~~

Just after startup, this would show that the current counter value is 0:

~~~~json
{
    "request": {
        "mbean": "count-service:name=puppetlabs.localhost.count-service-report-me",
        "type": "read"
    },
    "status": 200,
    "timestamp": 1486831476,
    "value": {
        "Count": 0
    }
}
~~~~

Make a request to the count service's endpoint to increment the counters:

~~~~
curl http://127.0.0.1:8080/count
~~~~

When the `.../metrics/v2` request from above is repeated, the value returned from
JMX should have increased by 1:

~~~~json
{
    "request": {
        "mbean": "count-service:name=puppetlabs.localhost.count-service-report-me",
        "type": "read"
    },
    "status": 200,
    "timestamp": 1486831664,
    "value": {
        "Count": 1
    }
}
~~~~
