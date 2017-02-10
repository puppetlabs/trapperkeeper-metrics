(ns puppetlabs.trapperkeeper.services.metrics.metrics-core-test
  (:import (com.codahale.metrics MetricRegistry JmxReporter)
           (com.puppetlabs.trapperkeeper.metrics GraphiteReporter)
           (clojure.lang ExceptionInfo))
  (:require [clojure.test :refer :all]
            [puppetlabs.trapperkeeper.testutils.logging :refer :all]
            [puppetlabs.trapperkeeper.services.metrics.metrics-core :as core]
            [puppetlabs.trapperkeeper.services.metrics.metrics-testutils :as utils]
            [schema.test :as schema-test]
            [puppetlabs.kitchensink.core :as ks]))

(use-fixtures :once schema-test/validate-schemas)

(deftest test-initialize-registry-context
  (testing "it logs if :enabled is provided"
    (with-test-logging
      (let [context (core/initialize-registry-context {:server-id "localhost" :enabled false} nil)]
        (is (logged? #"^Metrics are now always enabled." :warn))
        (is (instance? MetricRegistry (:registry context))))))
  (testing "initializes registry and adds to context"
    (doseq [domain ["my.epic.domain" :my.epic.domanin]]
      (let [context (core/initialize-registry-context {:server-id "localhost"} domain)]
        (is (instance? MetricRegistry (:registry context)))
        (is (nil? (:jmx-reporter context))))))
  (testing "enables jmx reporter if configured to do so"
    (let [context (core/initialize-registry-context
                   {:server-id "localhost"
                    :registries
                    {:foo.bar.baz
                     {:reporters
                      {:jmx {:enabled true}}}}} "foo.bar.baz")]
      (is (instance? MetricRegistry (:registry context)))
      (is (instance? JmxReporter (:jmx-reporter context)))))
  (testing "does not enable jmx reporter if configured to not do so"
    (let [context (core/initialize-registry-context
                   {:server-id "localhost"
                    :registries
                    {:foo.bar.baz
                     {:reporters
                      {:jmx {:enabled false}}}}} "foo.bar.baz")]
      (is (instance? MetricRegistry (:registry context)))
      (is (nil? (:jmx-reporter context))))))

(deftest test-lifecycle
  (testing "enables graphite reporter if configured to do so"
    (let [context (-> utils/test-config
                      (assoc-in [:registries :default :reporters :graphite :enabled] true)
                      core/create-initial-service-context
                      core/add-graphite-reporters)
          default-registry (get @(:registries context) :default)]
      (is (instance? MetricRegistry (:registry default-registry)))
      (is (instance? GraphiteReporter (:graphite-reporter default-registry)))
      (core/stop default-registry)))
  (testing "does not enable graphite reporter if not configured to do so"
    (let [context (-> utils/test-config
                      core/create-initial-service-context
                      core/add-graphite-reporters)
          default-registry (get @(:registries context) :default)]
      (is (instance? MetricRegistry (:registry default-registry)))
      (is (nil? (:graphite-reporter default-registry))))))

(deftest get-graphite-config-test
  (testing "get-graphite-config function works with `reporters` section"
    (let [config (utils/build-config-with-registries
                  {:no.graphite.settings {:metrics-allowed ["foo" "bar"]}
                   :enabled.graphite {:reporters {:graphite {:enabled true}}}
                   :new.interval.graphite {:reporters
                                           {:graphite {:enabled true
                                                       :update-interval-seconds 42}}}
                   :disabled.graphite {:reporters {:graphite {:enabled false}}}})]
      (testing "returns defaults if no registry settings in config"
        (is (= {:enabled false
                :host "my.graphite.server"
                :port 2003
                :update-interval-seconds 10}
               (core/get-graphite-config config :not.in.the.config))))
      (testing "returns disabled graphite if no registry-specific graphite settings"
        (is (= {:enabled false
                :host "my.graphite.server"
                :port 2003
                :update-interval-seconds 10}
               (core/get-graphite-config config :no.graphite.settings))))
      (testing "returns defaults with enabled graphite for registry with graphite enabled"
        (is (= {:enabled true
                :host "my.graphite.server"
                :port 2003
                :update-interval-seconds 10}
               (core/get-graphite-config config :enabled.graphite))))
      (testing "returns overrides for registry with graphite overrides"
        (is (= {:enabled true
                :host "my.graphite.server"
                :port 2003
                :update-interval-seconds 42}
               (core/get-graphite-config config :new.interval.graphite))))
      (testing "sets enabled to false if specified for the registry"
        (is (= {:enabled false
                :host "my.graphite.server"
                :port 2003
                :update-interval-seconds 10}
               (core/get-graphite-config config :no.graphite))))
      (testing "get-graphite-config function does the right thing without `reporters` section"
        (testing "returns nil if no graphite settings are in the config"
          (is (= nil (core/get-graphite-config {:server-id "localhost"} :foo))))
        (testing "throws error if output does not include full Graphite config"
          (is (thrown? ExceptionInfo
                       (core/get-graphite-config
                        {:server-id "localhost"
                         :registries {:incomplete.graphite
                                      {:reporters {:graphite {:enabled true
                                                              :update-interval-seconds 10}}}}}
                        :incomplete.graphite))))
        (testing "works if output does include full Graphite config"
          (is (= {:enabled true
                  :host "my.graphite.server"
                  :port 2003
                  :update-interval-seconds 10}
                 (core/get-graphite-config
                  {:server-id "localhost"
                   :registries {:graphite.set.in.registry
                                {:reporters
                                 (assoc-in utils/graphite-config [:graphite :enabled] true)}}}
                  :graphite.set.in.registry))))))))

(deftest get-or-initialize-test
  (let [config (utils/build-config-with-registries {:pre-existing {:reporters {:jmx {:enabled true}}}})
        context (core/create-initial-service-context config)]
    (testing "returns existing registry if already created"
      (let [pre-existing-registry (:pre-existing @(:registries context))]
        (is (instance? MetricRegistry (:registry pre-existing-registry)))
        (is (instance? JmxReporter (:jmx-reporter pre-existing-registry)))
        (is (= pre-existing-registry
               (core/get-or-initialize-registry-context context :pre-existing)))))
    (testing "creates a new registry if it does not already exist in context"
      (is (nil? (:new-registry @(:registries context))))
      (let [new-registry (core/get-or-initialize-registry-context context :new-registry)]
        (is (instance? MetricRegistry (:registry new-registry)))
        (testing "updates the context to have the new registry"
          (is (= new-registry (:new-registry @(:registries context)))))))))

(deftest add-graphite-reporters-test
  (testing "add-graphite-reporters function"
    (let [config (utils/build-config-with-registries
                  {:enabled.graphite {:reporters {:graphite {:enabled true}}}
                   :disabled.graphite {:reporters {:graphite {:enabled false}}}
                   :enabled.graphite.with-defaults {:reporters {:graphite {:enabled true}}}
                   :disabled.graphite.with-defaults
                   {:reporters {:graphite {:enabled false}}}
                   :enabled.graphite.with.metrics-allowed
                   {:metrics-allowed ["foo"]
                    :reporters {:graphite {:enabled true}}}
                   :disabled.graphite.with.metrics-allowed
                   {:metrics-allowed ["foo"]
                    :reporters {:graphite {:enabled false}}}})
          context (core/create-initial-service-context config)
          get-graphite-reporter (fn [domain]
                                  (get-in @(:registries context) [domain :graphite-reporter]))]
      (core/initialize-registry-settings
       context :enabled.graphite.with-defaults {:default-metrics-allowed ["bar"]})
      (core/initialize-registry-settings
       context :disabled.graphite.with-defaults {:default-metrics-allowed ["bar"]})
      (core/initialize-registry-settings
       context :not-in-config.with-defaults {:default-metrics-allowed ["bar"]})
      (core/get-or-initialize-registry-context context :not-in-config)
      (core/add-graphite-reporters context)
      (testing "adds graphite reporter for registry with graphite enabled"
        (is (instance? GraphiteReporter (get-graphite-reporter :enabled.graphite))))
      (testing "doesn't add graphite reporter for registry with graphite disabled"
        (is (nil? (get-graphite-reporter :disabled.graphite))))
      (testing (str "adds graphite reporter for registry with default metrics allwowed"
                    " and graphite enabled")
        (is (instance? GraphiteReporter (get-graphite-reporter :enabled.graphite))))
      (testing (str "doesn't add graphite reporter for registry with default metrics allowed"
                    " but graphite disabled")
        (is (nil? (get-graphite-reporter :disabled.graphite.with-defaults))))
      (testing (str "adds graphite reporter for registry with metrics allowed"
                    " and graphite enabled")
        (is (instance? GraphiteReporter
                       (get-graphite-reporter :enabled.graphite.with.metrics-allowed))))
      (testing (str "doesn't add graphite reporter for registry with metrics allowed"
                    " but graphite disabled")
        (is (nil? (get-graphite-reporter :disabled.graphite.with.metrics-allowed))))
      (testing "doesn't add graphite reporter for registry not in config"
        (is (nil? (get-graphite-reporter :not-in-config))))
      (testing "doesn't add graphite for registry with default metrics-allowed not in config"
        (is (nil? (get-graphite-reporter :not-in-config.with-defaults))))
      (core/stop-all context))))

(deftest initialize-registries-from-config-test
  (testing "initialize-registries-from-config + maybe-add-default-to-config creates default registry"
    (is (= [:default] (keys (core/initialize-registries-from-config
                             (core/maybe-add-default-to-config {:server-id "localhost"}))))))
  (testing (str "initialize-registries-from-config + maybe-add-default-to-config creates registries"
                " for everything in config plus default")
    (let [registries (core/initialize-registries-from-config
                      (core/maybe-add-default-to-config
                       {:server-id "localhost"
                        :registries {:foo {:reporters {:jmx {:enabled true}}
                                           :metrics-allowed ["foo"]}}}))]
      (is (= #{:default :foo} (ks/keyset registries)))
      (is (instance? MetricRegistry (get-in registries [:default :registry])))
      (is (instance? MetricRegistry (get-in registries [:foo :registry])))

      (testing "adds jmx reporter if configured"
        (is (nil? (get-in registries [:default :jmx-reporter])))
        (is (instance? JmxReporter (get-in registries [:foo :jmx-reporter]))))))
  (testing "initialize-registries-from-config creates default with settings from config"
    (let [registries (core/initialize-registries-from-config
                      (core/maybe-add-default-to-config
                       {:server-id "localhost"
                        :registries {:default {:reporters {:jmx {:enabled true}}}
                                     :foo {:metrics-allowed ["foo"]}}}))]
      (is (= #{:default :foo} (ks/keyset registries)))
      (is (instance? MetricRegistry (get-in registries [:default :registry])))
      (is (instance? MetricRegistry (get-in registries [:foo :registry])))

      (testing "adds jmx reporter if configured"
        (is (instance? JmxReporter (get-in registries [:default :jmx-reporter])))
        (is (nil? (get-in registries [:foo :jmx-reporter])))))))

(deftest initialize-registry-settings-test
  (let [context (core/create-initial-service-context utils/test-config)]
    (testing "initialize-registry-settings adds settings for a registry"
      (is (= {:foo.bar {:default-metrics-allowed ["foo.bar"]}}
             (core/initialize-registry-settings context
                                                :foo.bar
                                                {:default-metrics-allowed ["foo.bar"]}))))
    (testing "initialize-registry-settings throws an error if it is called after init lifecycle phase"
      (is (thrown? RuntimeException (core/initialize-registry-settings
                                     (core/lock-registry-settings context)
                                     :nope {:default-metrics-allowed ["default"]}))))
    (testing "initialize-registry-settings throws an error for a registry that already has settings"
      (core/initialize-registry-settings context
                                         :error.registry
                                         {:default-metrics-allowed ["foo.bar"]})
      (is (thrown? RuntimeException (core/initialize-registry-settings
                                     context
                                     :error.registry
                                     {:default-metrics-allowed ["another"]}))))
    ; Make sure all the graphite reporters get shutdown, otherwise they spawn background threads
    (core/stop-all context)))

(deftest ^:unit construct-metric-names-test
  (testing "prefix is correctly added"
    (let [metrics ["one" "two" "two"]
          prefixed-metrics (core/construct-metric-names "foo.bar" metrics)]
      (is (= #{"foo.bar.one" "foo.bar.two"} prefixed-metrics)))))

(deftest get-metrics-allowed-test
  (testing "get-metrics-allowed function")
  (let [config (utils/build-config-with-registries
                {:with-metrics-allowed {:metrics-allowed ["foo"]}
                 :with-metrics-allowed-and-defaults {:metrics-allowed ["foo"]}
                 :with-prefix {:metric-prefix "prefix"}
                 :with-prefix-and-defaults {:metric-prefix "prefix"}
                 :with-prefix-and-metrics-allowed {:metric-prefix "prefix"
                                                   :metrics-allowed ["foo"]}
                 :with-prefix-defaults-and-metrics-allowed {:metric-prefix "prefix"
                                                            :metrics-allowed ["foo"]}
                 :without-metrics-allowed-or-prefix {:reporters {:jmx {:enabled true}}}})]
    (testing "works with metrics allowed in config"
      (is (= #{"puppetlabs.localhost.foo"} (core/get-metrics-allowed config {} :with-metrics-allowed))))
    (testing "works with default metrics allowed"
      (is (= #{"puppetlabs.localhost.default"}
             (core/get-metrics-allowed config
                                       {:with-defaults {:default-metrics-allowed ["default"]}}
                                       :with-defaults))))
    (testing "works with metrics allowed in config and defaults"
      (is (= #{"puppetlabs.localhost.foo" "puppetlabs.localhost.default"}
             (core/get-metrics-allowed config {:with-metrics-allowed-and-defaults
                                               {:default-metrics-allowed ["default"]}}
                                       :with-metrics-allowed-and-defaults))))
    (testing "removes duplicates between metrics allowed in config and defaults"
      (is (= #{"puppetlabs.localhost.foo" "puppetlabs.localhost.default"}
             (core/get-metrics-allowed config {:with-metrics-allowed-and-defaults
                                               {:default-metrics-allowed ["default" "foo"]}}
                                       :with-metrics-allowed-and-defaults))))
    (testing "returns empty set if prefix but not metrics allowed"
      (is (= #{} (core/get-metrics-allowed config {} :with-prefix))))
    (testing "works with prefix and defaults"
      (is (= #{"prefix.default"}
             (core/get-metrics-allowed config
                                       {:with-prefix-and-defaults
                                        {:default-metrics-allowed ["default"]}}
                                       :with-prefix-and-defaults))))
    (testing "works with prefix and metrics allowed"
      (is (= #{"prefix.foo"}
             (core/get-metrics-allowed config
                                       {}
                                       :with-prefix-and-metrics-allowed))))
    (testing "works with prefix, defaults, and metrics allowed in config"
      (is (= #{"prefix.default" "prefix.foo"}
             (core/get-metrics-allowed config
                                       {:with-prefix-defaults-and-metrics-allowed
                                        {:default-metrics-allowed ["default"]}}
                                       :with-prefix-defaults-and-metrics-allowed))))
    (testing "returns empty set without metrics allowed or prefix"
      (is (= #{} (core/get-metrics-allowed config {} :without-metrics-allowed-or-prefix))))
    (testing "returns empty set for registry not in config"
      (is (= #{} (core/get-metrics-allowed config {} :not-in-config))))
    (testing "returns empty set when there is no registry config"
      (is (= #{} (core/get-metrics-allowed {:server-id "localhost"} {} :not-in-config))))))

(deftest ^:unit allowed-names-metrics-filter-match-test
  ; Wrap .matches so nil doesn't have to be passed every time
  (let [matches (fn [metric-filter name] (.matches metric-filter name nil))]
    (let [metrics-allowed #{"foo"
                            "example.domain"
                            "example.domain.more.specific"
                            "compiler.evaluate_resource.Class[Puppet_enterprise::Profile::Console]"}
          metric-filter (core/build-metric-filter metrics-allowed)]
      (testing "allowed strings match"
        (is (true? (matches metric-filter "foo")))
        (is (true? (matches metric-filter "example.domain")))
        (is (true? (matches metric-filter "example.domain.more.specific")))
        (is (true? (matches metric-filter "compiler.evaluate_resource.Class[Puppet_enterprise::Profile::Console]"))))

      (testing "non allowed strings don't match"
        (is (false? (matches metric-filter "foo.bar")))
        (is (false? (matches metric-filter "example.domain.more")))
        (is (false? (matches metric-filter "example")))
        (is (false? (matches metric-filter "Class[Puppet_enterprise::Profile::Console]")))))

    (testing "empty allow-metrics list allows everything"
      (let [metric-filter (core/build-metric-filter #{})]
        (is (matches metric-filter "anything"))))))

(deftest get-metric-prefix-test
  (testing "get-metric-prefix chooses the correct prefix"
    (testing "no metric-prefix provided"
      (is (= "puppetlabs.localhost" (core/get-metric-prefix utils/test-config :default))))
    (testing "metric-prefix provided"
      (let [config (assoc-in utils/test-config [:registries :default :metric-prefix] "test-prefix")]
        (is (= "test-prefix" (core/get-metric-prefix config :default)))))))

(deftest graphite-reporter-filter-test
  (let [registry-context (core/initialize-registry-context utils/test-config :default)
        registry (:registry registry-context)
        reported-metrics (atom {})
        metrics-allowed #{"test-histogram" "test-meter" "test-timer"}
        graphite-reporter (core/build-graphite-reporter
                           registry
                           metrics-allowed
                           (utils/make-graphite-sender reported-metrics :default))]

    ; Create a few Metric objects of the types we'd like to test
    (.histogram registry "test-histogram")
    (.histogram registry "should-not-be-reported")
    (.meter registry "test-meter")
    (.timer registry "test-timer")

    ; Call report once so our GraphiteSender instance can collect some data
    (.report graphite-reporter)

    (testing "Graphite reporter only sends whitelisted metrics"
      (is (utils/reported? @reported-metrics "test-histogram.mean"))
      (is (not (utils/reported? @reported-metrics "should-not-be-reported.mean"))))

    (testing "Graphite reporter sends desired histogram fields"
      (is (utils/reported? @reported-metrics "test-histogram.mean"))
      (is (utils/reported? @reported-metrics "test-histogram.min"))
      (is (utils/reported? @reported-metrics "test-histogram.max"))
      (is (utils/reported? @reported-metrics "test-histogram.stddev"))
      (is (utils/reported? @reported-metrics "test-histogram.p50"))
      (is (utils/reported? @reported-metrics "test-histogram.p75"))
      (is (utils/reported? @reported-metrics "test-histogram.p95")))

    (testing "Graphite reporter doesn't send any other histogram fields"
      (is (not (utils/reported? @reported-metrics "test-histogram.p98")))
      (is (not (utils/reported? @reported-metrics "test-histogram.p99")))
      (is (not (utils/reported? @reported-metrics "test-histogram.p999"))))

    (testing "Graphite reporter sends desired timer fields"
      (is (utils/reported? @reported-metrics "test-timer.mean"))
      (is (utils/reported? @reported-metrics "test-timer.min"))
      (is (utils/reported? @reported-metrics "test-timer.max"))
      (is (utils/reported? @reported-metrics "test-timer.stddev"))
      (is (utils/reported? @reported-metrics "test-timer.p50"))
      (is (utils/reported? @reported-metrics "test-timer.p75"))
      (is (utils/reported? @reported-metrics "test-timer.p95")))

    (testing "Graphite reporter doesn't send any other timer fields"
      (is (not (utils/reported? @reported-metrics "test-timer.p98")))
      (is (not (utils/reported? @reported-metrics "test-timer.p99")))
      (is (not (utils/reported? @reported-metrics "test-timer.p999"))))

    (testing "Graphite reporter sends desired meter fields"
      (is (utils/reported? @reported-metrics "test-meter.count")))

    (testing "Graphite reporter doesn't send any other meter fields"
      (is (not (utils/reported? @reported-metrics "test-meter.mean_rate")))
      (is (not (utils/reported? @reported-metrics "test-meter.m1_rate")))
      (is (not (utils/reported? @reported-metrics "test-meter.m5_rate")))
      (is (not (utils/reported? @reported-metrics "test-meter.m15_rate"))))))
