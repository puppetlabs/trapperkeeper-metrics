(ns puppetlabs.trapperkeeper.services.metrics.metrics-service-test
  (:import (com.codahale.metrics MetricRegistry))
  (:require [clojure.test :refer :all]
            [puppetlabs.trapperkeeper.services.metrics.metrics-service :refer :all]
            [puppetlabs.trapperkeeper.services.protocols.metrics :as metrics-protocol]
            [schema.test :as schema-test]
            [puppetlabs.trapperkeeper.testutils.bootstrap :refer [with-app-with-config]]
            [puppetlabs.trapperkeeper.app :as app]))

(use-fixtures :once schema-test/validate-schemas)

(deftest test-metrics-service
  (testing "Can boot metrics service and access registry"
    (with-app-with-config app [metrics-service] {:metrics {:server-id "localhost"}}
      (let [svc (app/get-service app :MetricsService)]
        (is (instance? MetricRegistry (metrics-protocol/get-metrics-registry svc)))))))
