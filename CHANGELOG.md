## 2.0.2
* pin ring-middleware to 2.0.2 until it's safe to move clj-parent to jetty10.

## 2.0.1
* update tk-jetty-10 to 1.0.8 to address broken ring handler getRequestCharacterEncoding() function and set default character encoding of ring handler responses to UTF-8.

## 2.0.0
* major breaking change to Jetty 10 using the trapperkeeper-webserver-jetty10 service.

## 1.5.0

* update clj-parent to 5.2.6 to allow use of bouncy-castle libs `18on` that replaced the `15on` naming 

## 1.4.3 (1.4.2 release failed, do not use)

* Update project specification to work correctly in FIPS mode.

## 1.4.1

* Update Jolokia to 1.7.0. This is a routine maintenance bump that includes some
  cleanup of reflective accesses that trigger warnings in Java 9 and newer.

## 1.4.0

* [PE-28647](https://tickets.puppetlabs.com/browse/PE-28647) - Allow
  remote connections to the v2 metrics endpoint now that the connection
  can be authenticated.

## 1.3.0

* [TK-489](https://tickets.puppetlabs.com/browse/TK-489) - Add
  trapperkeeper-authorization support. See that project for
  configuration. The jolokia access configuration still defaults to
  disallowing remote access, so this needs to be overridden by the user
  if using tk-authorization.

## 1.2.3

* [TK-486](https://tickets.puppetlabs.com/browse/TK-486) - Deprecate the v1
  endpoint, so it can be removed in a future release. Going forward all metrics
  gathering should go through the v2 endpoint, access to which can be flexibly
  controlled via jolokia access rules.

## 1.2.2

* Publicly published version of 1.2.1.

## 1.2.1

This is an internal-only release.

* [PE-28468](https://tickets.puppetlabs.com/browse/PE-28468) - Disable the v1
  metrics endpoint and restrict v2 to localhost-only access, to fix a CVE where
  sensitive Puppet data could appear in metrics names.

## 1.2.0

* Update Jolokia to 1.6.0. This was a security release of Jolokia, though we
  do not use the affected features of Jolokia it is still recommended to
  upgrade.

## 1.1.0

* [TK-442](https://tickets.puppetlabs.com/browse/TK-442) - Updates dropwizard
  from 3.1.2 to 3.2.2. NOTE: This is done by bumping the dependency on clj-parent
  which also brings in a new version of tk-jetty9. Java 7 is no longer supported.

## 1.0.0

This is a major backwards incompatible feature release.
The major new feature is support for exporting to graphite. See the
[documentation](documentation/configuration.md) for details about how to enable
graphite exporting.

The breaking changes are:

* The `metrics` section of the configuration has changed to allow configuration
  of jmx and graphite reporters per domain instead of globally. See the
  [documentation](documentation/configuration.md) for details about the new
  format.
* Domains must be specified as keywords instead of strings in the clojure API.
* `initialize-registry-settings` has been renamed to `update-registry-settings`

Additional changes:

* Allow multiple services to register `default-metrics-allowed` lists through
  the `update-registry-settings` service function to limit the number of
  metrics sent to graphite

Jira Links:

* [TK-361](https://tickets.puppetlabs.com/browse/TK-361) - Move
  pe-trapperkeeper-metrics code, including graphite exporting, into OSS 
  trapperkeeper-metrics.
* [TK-393](https://tickets.puppetlabs.com/browse/TK-393) - Make jmx enabled
  per-registry. Deprecate enabling jmx globally
* [TK-394](https://tickets.puppetlabs.com/browse/TK-394) - Only accept metrics
  domain as keyword instead of string
* [TK-436](https://tickets.puppetlabs.com/browse/TK-436) - Add support for
  appending registry settings from multiple services


## 0.6.0

This is a feature release.

* [TK-433](https://tickets.puppetlabs.com/browse/TK-433) - Add a new
  `get-server-id` services function.
* [TK-430](https://tickets.puppetlabs.com/browse/TK-430) - Bump clj-parent to
  0.3.3 and i18n to 0.6.0

## 0.5.0

This is a feature release that also contains bug fixes.

* [TK-404](https://tickets.puppetlabs.com/browse/TK-404) - Added a new "v2" API that is based on the [Jolokia](https://jolokia.org) library. Jolokia
  provides a superset of functionality found in the current "v1" metrics API. Refer to 'document/configuration.md'
  for more information on setting up Jolokia with Trapperkeeper. The [Jolokia Documentation](https://jolokia.org/reference/html/protocol.html)
  has the specifics on the new metrics API.

* [TK-427](https://tickets.puppetlabs.com/browse/TK-427) - Ensure the JSON request is fully parsed before closing the input stream of the request

* [TK-394](https://tickets.puppetlabs.com/browse/TK-394) - Tolerate either keywords or strings for metric domains

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
