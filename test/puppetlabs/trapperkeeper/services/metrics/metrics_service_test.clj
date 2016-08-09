(ns puppetlabs.trapperkeeper.services.metrics.metrics-service-test
  (:import (com.codahale.metrics MetricRegistry))
  (:require [clojure.test :refer :all]
            [cheshire.core :as json]
            [puppetlabs.http.client.sync :as http-client]
            [puppetlabs.metrics :as metrics]
            [puppetlabs.trapperkeeper.services.metrics.metrics-service :refer :all]
            [puppetlabs.trapperkeeper.services.protocols.metrics :as metrics-protocol]
            [schema.test :as schema-test]
            [puppetlabs.trapperkeeper.services.webrouting.webrouting-service :as webrouting-service]
            [puppetlabs.trapperkeeper.services.webserver.jetty9-service :as jetty9-service]
            [puppetlabs.trapperkeeper.testutils.bootstrap :refer [with-app-with-config]]
            [puppetlabs.trapperkeeper.app :as app]
            [puppetlabs.kitchensink.core :as ks]))

(use-fixtures :once schema-test/validate-schemas)

(defn parse-response
  ([resp]
   (parse-response resp false))
  ([resp keywordize?]
   (-> resp :body slurp (json/parse-string keywordize?))))

(def metrics-service-config
  {:metrics {:server-id "localhost"
             :reporters {:jmx {:enabled true}}}
   :webserver {:port 8180
               :host "0.0.0.0"}
   :web-router-service {:puppetlabs.trapperkeeper.services.metrics.metrics-service/metrics-webservice
                        "/metrics"}})

(deftest test-metrics-service
  (testing "Can boot metrics service and access registry"
    (with-app-with-config
     app
     [jetty9-service/jetty9-service
      webrouting-service/webrouting-service
      metrics-service
      metrics-webservice]
     metrics-service-config

     (testing "metrics service functions"
       (let [svc (app/get-service app :MetricsService)]
         (testing "`get-metrics-registry` called without domain works"
           (is (instance? MetricRegistry (metrics-protocol/get-metrics-registry svc))))

         (testing "`get-metrics-registry` called with domain works"
           (is (instance? MetricRegistry
                          (metrics-protocol/get-metrics-registry svc "pl.foo.reg"))))

         (testing "`initialize-registry-settings` throws an error because it is not yet implemented"
           (is (thrown? RuntimeException
                        (metrics-protocol/initialize-registry-settings svc "foo" {"foo" "bar"}))))))

     (testing "returns latest status for all services"
       (let [resp (http-client/get "http://localhost:8180/metrics/v1/mbeans")
             body (parse-response resp)]
         (is (= 200 (:status resp)))
         (doseq [[metric path] body
                 :let [resp (http-client/get (str "http://localhost:8180/metrics/v1" path))]]
           (is (= 200 (:status resp))))))

      (testing "register should add a metric to the registry"
        (let [svc (app/get-service app :MetricsService)
              registry (metrics-protocol/get-metrics-registry svc "pl.foo.reg")]
          (metrics/register registry
                            (metrics/host-metric-name "localhost" "foo")
                            (metrics/gauge 2))
          (let [resp (http-client/get
                      (java.net.URLDecoder/decode
                       (str "http://localhost:8180/metrics/v1/mbeans/"
                            "pl.foo.reg:name=puppetlabs.localhost.foo")))
                body (parse-response resp)]
            (is (= 200 (:status resp)))
            (is (= {"Value" 2} body)))))


      (testing "querying multiple metrics via POST should work"
        (let [svc (app/get-service app :MetricsService)
              registry (metrics-protocol/get-metrics-registry svc "pl.other.reg")]
          (metrics/register registry
                            (metrics/host-metric-name "localhost" "foo")
                            (metrics/gauge 2))
          (metrics/register registry
                            (metrics/host-metric-name "localhost" "bar")
                            (metrics/gauge 500))

          (let [resp (http-client/post
                      "http://localhost:8180/metrics/v1/mbeans"
                      {:body (json/generate-string
                              ["pl.other.reg:name=puppetlabs.localhost.foo"
                               "pl.other.reg:name=puppetlabs.localhost.bar"])})
                body (parse-response resp)]
            (is (= 200 (:status resp)))
            (is (= [{"Value" 2} {"Value" 500}] body)))

          (let [resp (http-client/post
                      "http://localhost:8180/metrics/v1/mbeans"
                      {:body (json/generate-string
                              {:foo "pl.other.reg:name=puppetlabs.localhost.foo"
                               :bar "pl.other.reg:name=puppetlabs.localhost.bar"})})
                body (parse-response resp)]
            (is (= 200 (:status resp)))
            (is (= {"foo" {"Value" 2}
                    "bar" {"Value" 500}} body)))

          (let [resp (http-client/post
                      "http://localhost:8180/metrics/v1/mbeans"
                      {:body (json/generate-string
                              "pl.other.reg:name=puppetlabs.localhost.foo")})
                body (parse-response resp)]
            (is (= 200 (:status resp)))
            (is (= {"Value" 2} body)))

          (let [resp (http-client/post
                      "http://localhost:8180/metrics/v1/mbeans"
                      {:body "{\"malformed json"})
                body (slurp (:body resp))]
            (is (= 400 (:status resp)))
            (is (re-find #"Unexpected end-of-input" body))))))))

(deftest metrics-endpoint-with-jmx-disabled-test
  (testing "returns data for jvm even when jmx is not enabled"
    (let [config (assoc-in metrics-service-config [:metrics :reporters :jmx :enabled] false)]
      (with-app-with-config
       app
       [jetty9-service/jetty9-service
        webrouting-service/webrouting-service
        metrics-service
        metrics-webservice]
       config
       (testing "returns latest status for all services"
         (let [resp (http-client/get "http://localhost:8180/metrics/v1/mbeans")
               body (parse-response resp)]
           (is (= 200 (:status resp)))
           (is (not (empty? body)))))
       (testing "returns Memoory mbean information"
         (let [resp (http-client/get "http://localhost:8180/metrics/v1/mbeans/java.lang%3Atype%3DMemory")
               body (parse-response resp)
               heap-memory (get body "HeapMemoryUsage")]
           (is (= 200 (:status resp)))
           (is (= #{"committed" "init" "max" "used"} (ks/keyset heap-memory)))
           (is (every? #(< 0 %) (vals heap-memory)))))))))
