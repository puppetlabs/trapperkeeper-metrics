(ns puppetlabs.trapperkeeper.services.metrics.metrics-core-test
  (:import (com.codahale.metrics MetricRegistry JmxReporter))
  (:require [clojure.test :refer :all]
            [puppetlabs.trapperkeeper.services.metrics.metrics-core :refer :all]
            [schema.test :as schema-test]))

(use-fixtures :once schema-test/validate-schemas)

(deftest test-initialize
  (testing "does not initialize registry if metrics are disabled"
    (let [context (initialize {:enabled false :server-id "localhost"})]
      (is (nil? (:registry context)))))
  (testing "initializes registry and adds to context if metrics are enabled"
    (let [context (initialize {:enabled true :server-id "localhost"})]
      (is (instance? MetricRegistry (:registry context)))
      (is (not (contains? context :jmx-reporter)))))
  (testing "enables jmx reporter if configured to do so"
    (let [context (initialize {:enabled true
                               :server-id "localhost"
                               :reporters
                               {:jmx {:enabled true}}})]
      (is (instance? MetricRegistry (:registry context)))
      (is (instance? JmxReporter (:jmx-reporter context) )))))