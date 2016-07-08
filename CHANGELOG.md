## 0.2.1

This is a minor maintenance and bugfix release.

* Lay groundwork for future i18n work
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
