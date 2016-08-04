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
