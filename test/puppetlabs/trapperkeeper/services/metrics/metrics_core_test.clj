(ns puppetlabs.trapperkeeper.services.metrics.metrics-core-test
  (:import (com.codahale.metrics MetricRegistry JmxReporter))
  (:require [clojure.test :refer :all]
            [metrics.core]
            [puppetlabs.trapperkeeper.services.metrics.metrics-core :refer :all]
            [schema.test :as schema-test]))

(use-fixtures :once schema-test/validate-schemas)

(deftest test-initialize
  (testing "does not initialize registry if metrics are disabled"
    (let [context (initialize {:enabled false :server-id "localhost"})]
      (is (nil? (:registry context)))))
  (testing "adds the metrics.core/default-registry to context"
    (let [context (initialize {:enabled true :server-id "localhost"})]
      (is (instance? MetricRegistry (:registry context)))
      (is (= metrics.core/default-registry (:registry context)))
      (is (not (contains? context :jmx-reporter)))))
  (testing "enables jmx reporter if configured to do so"
    (let [context (initialize {:enabled true
                               :server-id "localhost"
                               :reporters
                               {:jmx {:enabled true}}})]
      (is (instance? MetricRegistry (:registry context)))
      (is (instance? JmxReporter (:jmx-reporter context) )))))
