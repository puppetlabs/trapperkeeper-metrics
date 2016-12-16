(ns puppetlabs.trapperkeeper.services.metrics.metrics-service-test
  (:import (com.codahale.metrics MetricRegistry))
  (:require [clojure.test :refer :all]
            [cheshire.core :as json]
            [clojure.string :as string]
            [ring.util.codec :as codec]
            [puppetlabs.http.client.sync :as http-client]
            [puppetlabs.metrics :as metrics]
            [puppetlabs.trapperkeeper.services.metrics.metrics-service :refer :all]
            [puppetlabs.trapperkeeper.services.protocols.metrics :as metrics-protocol]
            [schema.test :as schema-test]
            [puppetlabs.trapperkeeper.services.webrouting.webrouting-service :as webrouting-service]
            [puppetlabs.trapperkeeper.services.webserver.jetty9-service :as jetty9-service]
            [puppetlabs.trapperkeeper.testutils.bootstrap :refer [with-app-with-config]]
            [puppetlabs.trapperkeeper.testutils.logging :refer [with-test-logging]]
            [puppetlabs.trapperkeeper.app :as app]
            [puppetlabs.kitchensink.core :as ks]))

(use-fixtures :once schema-test/validate-schemas)

(defn parse-response
  ([resp]
   (parse-response resp false))
  ([resp keywordize?]
   (-> resp :body slurp (json/parse-string keywordize?))))

(defn jolokia-encode
  "Encodes a MBean name according to the rules laid out in:

     https://jolokia.org/reference/html/protocol.html#escape-rules"
  [mbean-name]
  (-> mbean-name
      (string/escape {\/ "!/" \! "!!" \" "!\""})
      codec/url-encode))

(def test-resources-dir
  (ks/absolute-path "./dev-resources/puppetlabs/trapperkeeper/services/metrics/metrics_service_test"))

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
           (is (= 200 (:status resp)))))

       (let [resp (http-client/get "http://localhost:8180/metrics/v2/list")
             body (parse-response resp)]
         (is (= 200 (:status  resp)))
         (doseq [[namesp mbeans] (get body "value") mbean (keys mbeans)
                 :let [url (str "http://localhost:8180/metrics/v2/read/"
                                (jolokia-encode (str namesp ":" mbean))
                                ;; NOTE: Some memory pools intentionally don't
                                ;; implement MBean attributes. This results
                                ;; in an error being thrown when those
                                ;; attributes are read and is expected.
                                "?ignoreErrors=true")
                       resp (http-client/get url)
                       body (parse-response resp)]]
           ;; NOTE: Jolokia returns 200 OK for most responses. The actual
           ;; status code is in the JSON payload that makes up the body.
           (is (= 200 (get body "status"))))))

      (testing "register should add a metric to the registry"
        (let [svc (app/get-service app :MetricsService)
              register-and-get-metric (fn [domain metric]
                                        (metrics/register
                                          (metrics-protocol/get-metrics-registry svc domain)
                                          (metrics/host-metric-name "localhost" metric)
                                          (metrics/gauge 2))
                                        (http-client/get
                                          (str "http://localhost:8180/metrics/v1/mbeans/"
                                                 (codec/url-encode
                                                  (str (name domain) ":name=puppetlabs.localhost." metric)))))]

          (testing (str "with a keyword domain")
            (let [resp (register-and-get-metric :pl.test.reg "foo")]
              (is (= 200 (:status resp)))
              (is (= {"Value" 2} (parse-response resp)))))

          (testing (str "with a string domain")
            (let [resp (register-and-get-metric "pl.test.reg" "bar")]
              (is (= 200 (:status resp)))
              (is (= {"Value" 2} (parse-response resp)))))))

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
            (is (re-find #"Unexpected end-of-input" body)))

          (let [resp (http-client/post
                      "http://localhost:8180/metrics/v2"
                      {:body (json/generate-string
                               [{:type "read" :mbean "pl.other.reg:name=puppetlabs.localhost.foo"}
                                {:type "read" :mbean "pl.other.reg:name=puppetlabs.localhost.bar"}])})
                body (parse-response resp true)]
            (is (= [200 200] (map :status body)))
            (is (= [{:Value 2} {:Value 500}] (map :value body))))))

      (testing "metrics/v2 should deny write requests"
        (with-test-logging
          (let [resp (http-client/get
                       (str "http://localhost:8180/metrics/v2/write/"
                            (jolokia-encode "java.lang:type=Memory")
                            "/Verbose/true"))
                body (parse-response resp)]
            (is (= 403 (get body "status"))))))

      (testing "metrics/v2 should deny exec requests"
        (with-test-logging
          (let [resp (http-client/get
                       (str "http://localhost:8180/metrics/v2/exec/"
                            (jolokia-encode "java.util.logging:type=Logging")
                            "/getLoggerLevel/root"))
                body (parse-response resp)]
            (is (= 403 (get body "status")))))))))

(deftest metrics-endpoint-with-jolokia-disabled-test
  (testing "metrics/v2 returns 404 when Jolokia is not enabled"
    (let [config (assoc-in metrics-service-config [:metrics :metrics-webservice :jolokia :enabled] false)]
      (with-app-with-config
       app
       [jetty9-service/jetty9-service
        webrouting-service/webrouting-service
        metrics-service
        metrics-webservice]
       config
        (let [resp (http-client/get "http://localhost:8180/metrics/v2/version")]
          (is (= 404 (:status resp))))))))

(deftest metrics-endpoint-with-permissive-jolokia-policy
  (testing "metrics/v2 allows exec requests when configured with a permissive policy"
    (let [config (assoc-in metrics-service-config
                           [:metrics :metrics-webservice :jolokia :servlet-init-params :policyLocation]
                           (str "file://" test-resources-dir "/jolokia-access-permissive.xml"))]
      (with-app-with-config
       app
       [jetty9-service/jetty9-service
        webrouting-service/webrouting-service
        metrics-service
        metrics-webservice]
       config
        (let [resp (http-client/get
                     (str "http://localhost:8180/metrics/v2/exec/"
                          (jolokia-encode "java.util.logging:type=Logging")
                          "/getLoggerLevel/root"))
              body (parse-response resp)]
          (is (= 200 (get body "status"))))))))

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
