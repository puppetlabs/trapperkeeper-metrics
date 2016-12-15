## 0.5.0

This is a feature release that also contains bug fixes.

* [TK-404](https://tickets.puppetlabs.com/browse/TK-404) - Added a new "v2" API that is based on the [Jolokia](https://jolokia.org) library. Jolokia
  provides a superset of functionality found in the current "v1" metrics API. Refer to 'document/configureation.md'
  for more information on setting up Jolokia with Trapperkeeper. The [Jolokia Documentation](https://jolokia.org/documentation.html)
  has the specifics on the new metrics API.

* [TK-427](https://tickets.puppetlabs.com/browse/TK-427) - Ensure the JSON request is fully parsed before closing the input stream of the request

* Remove explicit ring-core, servlet-api, and slf4j-api deps

* Switch to using lein parent (and puppetlabs/clj-parent) for most dependency versions,
  which also now requires Leiningen 2.7.1 or greater

## 0.4.2

This is a bug fix release.

* Don't require JMX to be enabled for the metrics endpoint to work. Now, if
 the `metrics-webservice` is added to the config, the `/metrics` endpoint
 will always be registered.

## 0.4.1

This is a maintenance release.

* Bump puppetlabs/ring-middleware dependency from 0.3.1 to 1.0.0.

## 0.4.0

This is a feature release.

* Add an `initialize-registry-settings` function to the MetricsService
 protocol. The implementation of this function in the trapperkeeper-metrics
 service in this repo is not yet implemented and currently just throws an
 error.

## 0.3.0

This is a minor feature, maintenance, and bugfix release.

* Introduce i18n library and lay groundwork for future i18n work
* Update project.clj to prefer explicit dependencies instead of implicit transitive dependencies to resolve conflicts
* Extract common ring utils into puppetlabs/ring-middleware

## 0.2.0

This is a feature release.

* Add the ability to configure multiple metrics registries
* Add a new metrics-server service (ported from the PuppetDB `/metrics` API) for
  querying JMX metrics

## 0.1.2

This is a feature release.

* [TK-252](https://tickets.puppetlabs.com/browse/TK-252)
  Always build a metrics registry, deprecate metrics.enabled setting
* Add `mean-millis` and related utility fns
