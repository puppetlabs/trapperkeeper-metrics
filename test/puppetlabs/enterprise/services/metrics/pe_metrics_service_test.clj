(ns puppetlabs.enterprise.services.metrics.pe-metrics-service-test
  (:import (com.codahale.metrics MetricRegistry JmxReporter)
           (com.puppetlabs.enterprise PEGraphiteReporter))
  (:require [clojure.test :refer :all]
            [puppetlabs.enterprise.services.metrics.pe-metrics-service :refer :all]
            [puppetlabs.trapperkeeper.services.protocols.metrics :as metrics-protocol]
            [schema.test :as schema-test]
            [puppetlabs.trapperkeeper.testutils.bootstrap :refer [with-app-with-config]]
            [puppetlabs.trapperkeeper.testutils.logging :refer [with-test-logging]]
            [puppetlabs.trapperkeeper.app :as app]
            [puppetlabs.trapperkeeper.services :as tk-services]
            [puppetlabs.trapperkeeper.core :as trapperkeeper]
            [puppetlabs.kitchensink.core :as ks]
            [puppetlabs.enterprise.services.metrics.pe-metrics-testutils :as utils]
            [puppetlabs.enterprise.services.metrics.pe-metrics-core :as core]))

(use-fixtures :once schema-test/validate-schemas)

(deftest get-metrics-registry-service-function-test
  (with-app-with-config
   app
   [pe-metrics-service]
   {:metrics {:server-id "localhost"}}
   (let [svc (app/get-service app :MetricsService)]
     (testing "Can access default registry"
       (is (instance? MetricRegistry (metrics-protocol/get-metrics-registry svc))))
     (testing "Can access other registries registry"
       (is (instance? MetricRegistry (metrics-protocol/get-metrics-registry svc :my-domain)))))))

(deftest get-server-id-service-function-test
  (with-app-with-config
    app
    [pe-metrics-service]
    {:metrics {:server-id "foo"}}
    (let [svc (app/get-service app :MetricsService)]
      (testing "Can access server-id"
       (is (= "foo" (metrics-protocol/get-server-id svc)))))))

(deftest initialize-registry-settings-service-function-test
  (testing "intialize-registry-settings adds settings for a registry"
    (let [service (trapperkeeper/service
                   [[:MetricsService initialize-registry-settings]]
                   (init [this context]
                    (initialize-registry-settings :foo.bar {:default-metrics-allowed ["foo.bar"]})
                    context))
          metrics-app (trapperkeeper/build-app [service pe-metrics-service]
                                               {:metrics utils/test-config})
          pe-metrics-svc (app/get-service metrics-app :MetricsService)]
      (try
        (app/init metrics-app)
        (is (= {:foo.bar {:default-metrics-allowed ["foo.bar"]}}
               @(:registry-settings (tk-services/service-context pe-metrics-svc))))

        (finally
          (app/stop metrics-app)))))

  (testing "initialize-registry-settings throws an error for a registry that already has settings"
    (let [service (trapperkeeper/service
                   [[:MetricsService initialize-registry-settings]]
                   (init [this context]
                    (initialize-registry-settings
                     :error.registry {:default-metrics-allowed ["foo.bar"]})
                    (initialize-registry-settings
                     :error.registry {:default-metrics-allowed ["another"]})
                    context))
          metrics-app (trapperkeeper/build-app [service pe-metrics-service]
                                               {:metrics utils/test-config})]
      (with-test-logging
       (try
         (is (thrown? RuntimeException (app/check-for-errors! (app/init metrics-app))))
         (finally
           (app/stop metrics-app))))))

  (testing "initialize-registry-settings throws an error if called outside `init`"
    (let [service (trapperkeeper/service
                   [[:MetricsService initialize-registry-settings]]
                   (start [this context]
                    (initialize-registry-settings :nope {:default-metrics-allowed ["fail"]})
                    context))
          metrics-app (trapperkeeper/build-app [service pe-metrics-service]
                                               {:metrics utils/test-config})]
      (with-test-logging
       (try
         (is (thrown? RuntimeException (app/check-for-errors! (app/start metrics-app))))
         (finally
           (app/stop metrics-app)))))))

(deftest jmx-enabled-globally-deprecated-test
  (with-test-logging
   (with-app-with-config
    app
    [pe-metrics-service]
    {:metrics {:server-id "localhost"
               :reporters {:jmx {:enabled true}}}})
    (is (logged? #"Enabling JMX globally is deprecated; JMX can only be enabled per-registry."))))

(deftest jmx-works-test
  (with-app-with-config
   app
   [pe-metrics-service]
   {:metrics {:server-id "localhost"
              :registries {:jmx.registry {:reporters {:jmx {:enabled true}}}
                           :no.jmx.registry {:reporters {:jmx {:enabled false}}}
                           :foo {:metrics-allowed ["foo"]}}}}
   (let [svc (app/get-service app :MetricsService)
         context (tk-services/service-context svc)
         get-jmx-reporter (fn [domain] (get-in @(:registries context) [domain :jmx-reporter]))]
     (testing "Registry with jmx enabled gets a jmx reporter"
       (metrics-protocol/get-metrics-registry svc :jmx.registry)
       (is (instance? JmxReporter (get-jmx-reporter :jmx.registry))))
     (testing "Registry with jmx disabled does not get a jmx reporter"
       (metrics-protocol/get-metrics-registry svc :jmx.registry)
       (is (nil? (get-jmx-reporter :no.jmx.registry))))
     (testing "Registry with no mention of jmx does not get a jmx reporter"
       (metrics-protocol/get-metrics-registry svc :foo)
       (is (nil? (get-jmx-reporter :foo))))
     (testing "Registry not mentioned in config does not get a jmx reporter"
       (metrics-protocol/get-metrics-registry svc :not.in.the.config)
       (is (nil? (get-jmx-reporter :not.in.the.config)))))))

(defn create-meters!
  [registries meter-names]
  (doseq [{:keys [registry]} (vals registries)
          meter meter-names]
    (.meter registry meter )))

(defn report-to-graphite!
  [registries]
  (doseq [graphite-reporter (map :graphite-reporter (vals registries))]
    (when graphite-reporter
      (.report graphite-reporter))))

(def graphite-enabled
  {:reporters {:graphite {:enabled true}}})

(def metrics-allowed
  {:metrics-allowed ["not-default"]})

(def default-metrics-allowed
  {:default-metrics-allowed ["default"]})

(deftest integration-test
  (let [registries-config
        {:graphite-enabled graphite-enabled
         :graphite-with-default-metrics-allowed graphite-enabled
         :graphite-with-metrics-allowed (merge metrics-allowed graphite-enabled)
         :graphite-with-defaults-and-metrics-allowed (merge metrics-allowed graphite-enabled)}
        config {:metrics (utils/build-config-with-registries registries-config)}
        service (trapperkeeper/service
                 [[:MetricsService get-metrics-registry initialize-registry-settings]]
                 (init
                  [this context]
                  (get-metrics-registry :graphite-enabled)

                  (get-metrics-registry :graphite-with-default-metrics-allowed)
                  (initialize-registry-settings :graphite-with-default-metrics-allowed
                                                default-metrics-allowed)

                  (get-metrics-registry :graphite-with-metrics-allowed)

                  ;; shouldn't matter whether `get-metrics-registry` or
                  ;; `initialize-registry-settings` is called first
                  (initialize-registry-settings :graphite-with-defaults-and-metrics-allowed
                                                default-metrics-allowed)
                  (get-metrics-registry :graphite-with-defaults-and-metrics-allowed)

                  context))
        reported-metrics-atom (atom {})]
    (with-redefs [core/build-graphite-sender
                  (fn [_ domain] (utils/make-graphite-sender reported-metrics-atom domain))]
      (let [metrics-app (trapperkeeper/build-app
                         [pe-metrics-service service]
                         config)
            pe-metrics-svc (app/get-service metrics-app :MetricsService)
            get-context (fn [] (tk-services/service-context pe-metrics-svc))]
        (try
          (testing "init phase of lifecycle"
            (app/init metrics-app)
            (let [context (get-context)
                  registries @(:registries context)
                  registry-settings @(:registry-settings context)]
              (testing "all registries in config (plus default) get created"
                (is (= #{:default
                         :graphite-enabled
                         :graphite-with-default-metrics-allowed
                         :graphite-with-metrics-allowed
                         :graphite-with-defaults-and-metrics-allowed}
                       (ks/keyset registries)))
                (is (every? #(instance? MetricRegistry %)
                            (map :registry (vals registries)))))

              (testing "graphite reporters are not created in it"
                (is (every? nil? (map :graphite-reproter (vals registries)))))

              (testing "registry settings are initialized in init"
                (is (= {:graphite-with-default-metrics-allowed default-metrics-allowed
                        :graphite-with-defaults-and-metrics-allowed default-metrics-allowed}
                       registry-settings)))))

          (testing "start phase of lifecycle"
            (app/start metrics-app)

            (let [context (get-context)
                  registries @(:registries context)
                  registry-settings @(:registry-settings context)]

              (testing "graphite reporters are created in start"

                (is (every? #(instance? MetricRegistry %)
                            (map :registry (vals registries))))
                (is (every? #(instance? PEGraphiteReporter %)
                            (map :graphite-reporter (vals (dissoc registries :default)))))

                (is (nil? (get-in registries [:default :graphite-reporter]))))

              (testing "the right metrics are reported to graphite"
                (create-meters! registries ["puppetlabs.localhost.default"
                                            "puppetlabs.localhost.not-default"
                                            "puppetlabs.localhost.foo"])
                (report-to-graphite! registries)
                (let [reported-metrics @reported-metrics-atom]

                  (is (= #{:graphite-enabled
                           :graphite-with-default-metrics-allowed
                           :graphite-with-metrics-allowed
                           :graphite-with-defaults-and-metrics-allowed}
                         (ks/keyset reported-metrics)))

                  (testing "without any metrics filter configured all metrics are reported"
                    (is (utils/reported? reported-metrics
                                         :graphite-enabled
                                         "puppetlabs.localhost.foo.count"))
                    (is (utils/reported? reported-metrics
                                         :graphite-enabled
                                         "puppetlabs.localhost.default.count"))
                    (is (utils/reported? reported-metrics
                                         :graphite-enabled
                                         "puppetlabs.localhost.not-default.count")))

                  (testing "default metrics are reported to graphite"
                    (is (not (utils/reported? reported-metrics
                                              :graphite-with-default-metrics-allowed
                                              "puppetlabs.localhost.foo.count")))
                    (is (utils/reported? reported-metrics
                                         :graphite-with-default-metrics-allowed
                                         "puppetlabs.localhost.default.count"))
                    (is (not (utils/reported? reported-metrics
                                              :graphite-with-default-metrics-allowed
                                              "puppetlabs.localhost.not-default.count"))))

                  (testing "configured metrics are reported to graphite"
                    (is (not (utils/reported? reported-metrics
                                              :graphite-with-metrics-allowed
                                              "puppetlabs.localhost.foo.count")))
                    (is (not (utils/reported? reported-metrics
                                              :graphite-with-metrics-allowed
                                              "puppetlabs.localhost.default.count")))
                    (is (utils/reported? reported-metrics
                                         :graphite-with-metrics-allowed
                                         "puppetlabs.localhost.not-default.count")))

                  (testing "configured metrics and default allowed metrics are reported to graphite"
                    (is (not (utils/reported? reported-metrics
                                              :graphite-with-defaults-and-metrics-allowed
                                              "localhost.foo.count")))
                    (is (utils/reported? reported-metrics
                                         :graphite-with-defaults-and-metrics-allowed
                                         "puppetlabs.localhost.default.count"))
                    (is (utils/reported? reported-metrics
                                         :graphite-with-defaults-and-metrics-allowed
                                         "puppetlabs.localhost.not-default.count")))))))
          (finally
            (app/stop metrics-app)))))))
