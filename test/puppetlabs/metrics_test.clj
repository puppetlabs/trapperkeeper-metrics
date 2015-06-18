(ns puppetlabs.metrics-test
  (:import (com.codahale.metrics MetricRegistry Meter Timer)
           (java.util.concurrent TimeUnit))
  (:require [clojure.test :refer :all]
            [puppetlabs.metrics :refer :all]
            [schema.test :as schema-test]))

(use-fixtures :once schema-test/validate-schemas)

(deftest test-host-metric-name
  (testing "host-metric-name should create a metric name"
    (is (= "puppetlabs.localhost.foocount" (host-metric-name "localhost" "foocount")))))

(deftest test-http-metric-name
  (testing "http-metric-name should create a metric name"
    (is (= "puppetlabs.localhost.http.foocount" (http-metric-name "localhost" "foocount")))))

(deftest test-register
  (testing "register should add a metric to the registry"
    (let [registry (MetricRegistry.)]
      (register registry (host-metric-name "localhost" "foo") (gauge 2))
      (let [gauges (.getGauges registry)]
        (is (= 1 (count gauges)))
        (is (= "puppetlabs.localhost.foo" (first (.keySet gauges))))))))

(deftest test-ratio
  (testing "ratio should create a ratio metric"
    (let [numerator       (atom 4)
          numerator-fn    (fn [] @numerator)
          denominator-fn  (constantly 2)
          ratio-metric    (ratio numerator-fn denominator-fn)]
      (is (= 2.0 (.. ratio-metric getRatio getValue)))
      (reset! numerator 6)
      (is (= 3.0 (.. ratio-metric getRatio getValue))))))

(deftest test-metered-ratio
  (testing "metered-ratio builds a ratio metric from two counters"
    (let [numerator-meter     (Meter.)
          denominator-timer   (Timer.)
          ratio-metric        (metered-ratio numerator-meter denominator-timer)]
      (dotimes [_ 2] (.update denominator-timer 0 TimeUnit/MILLISECONDS))
      (dotimes [_ 4] (.mark numerator-meter))
      (is (= 2.0 (.. ratio-metric getRatio getValue)))
      (dotimes [_ 2] (.mark numerator-meter))
      (is (= 3.0 (.. ratio-metric getRatio getValue))))))

(deftest test-gauge
  (testing "gauge creates a gauge metric with an initial value"
    (let [gauge-metric (gauge 42)]
      (is (= 42 (.getValue gauge-metric))))))

(deftest test-time!
  (testing "time! will time the enclosed form"
    (let [timer (Timer.)]
      (time! timer
        (Thread/sleep 1))
      (is (= 1 (.getCount timer)))
      (let [elapsed (.toMillis TimeUnit/NANOSECONDS (.. timer getSnapshot getMean))]
        (is (>= elapsed 1))
        (is (<= elapsed 100))))))