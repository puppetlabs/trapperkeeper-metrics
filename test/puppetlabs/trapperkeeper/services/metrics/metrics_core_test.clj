(ns puppetlabs.trapperkeeper.services.metrics.metrics-core-test
  (:import (com.codahale.metrics MetricRegistry JmxReporter))
  (:require [clojure.test :refer :all]
            [puppetlabs.trapperkeeper.testutils.logging :refer :all]
            [puppetlabs.trapperkeeper.services.metrics.metrics-core :refer :all]
            [schema.test :as schema-test]))

(use-fixtures :once schema-test/validate-schemas)

(deftest test-initialize
  (testing "it logs if :enabled is provided"
    (with-test-logging
      (let [context (initialize {:server-id "localhost" :enabled false})]
        (is (logged? #"^Metrics are now always enabled." :warn))
        (is (instance? MetricRegistry (:registry context))))))
  (testing "initializes registry and adds to context"
    (let [context (initialize {:server-id "localhost"})]
      (is (instance? MetricRegistry (:registry context)))
      (is (not (contains? context :jmx-reporter)))))
  (testing "enables jmx reporter if configured to do so"
    (let [context (initialize {:server-id "localhost"
                               :reporters
                               {:jmx {:enabled true}}})]
      (is (instance? MetricRegistry (:registry context)))
      (is (instance? JmxReporter (:jmx-reporter context) )))))
