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
            [puppetlabs.trapperkeeper.app :as app]))

(use-fixtures :once schema-test/validate-schemas)

(defn parse-response
  ([resp]
   (parse-response resp false))
  ([resp keywordize?]
   (json/parse-string (slurp (:body resp)) keywordize?)))

(def metrics-service-config
  {:metrics {:server-id "localhost"
             :reporters {:jmx {:enabled true}}}
   :webserver {:port 8180
               :host "0.0.0.0"}
   :web-router-service {:puppetlabs.trapperkeeper.services.metrics.metrics-service/metrics-webservice
                        "/metrics"}})

(deftest test-metrics-service
  (testing "Can boot metrics service and access registry"
    (with-app-with-config app [jetty9-service/jetty9-service
                               webrouting-service/webrouting-service
                               metrics-service
                               metrics-webservice] metrics-service-config
      (let [svc (app/get-service app :MetricsService)]
        (is (instance? MetricRegistry (metrics-protocol/get-metrics-registry svc))))

      (let [svc (app/get-service app :MetricsService)]
        (is (instance? MetricRegistry
                       (metrics-protocol/get-metrics-registry svc "pl.foo.reg"))))

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
            (is (= {"Value" 2} body))))))))
