(ns puppetlabs.trapperkeeper.services.metrics.metrics-service-test
  (:import (com.codahale.metrics MetricRegistry JmxReporter)
           (clojure.lang ExceptionInfo)
           (com.puppetlabs.trapperkeeper.metrics GraphiteReporter))
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
            [puppetlabs.kitchensink.core :as ks]
            [puppetlabs.trapperkeeper.testutils.logging :as logging]
            [puppetlabs.trapperkeeper.core :as trapperkeeper]
            [puppetlabs.trapperkeeper.services.metrics.metrics-testutils :as utils]
            [puppetlabs.trapperkeeper.services :as tk-services]
            [puppetlabs.trapperkeeper.services.metrics.metrics-core :as core]))

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

(def services
  [jetty9-service/jetty9-service
   webrouting-service/webrouting-service
   metrics-service
   metrics-webservice])

(def metrics-service-config
  {:metrics {:server-id "localhost"
             :registries {:pl.test.reg {:reporters {:jmx {:enabled true}}}
                          :pl.other.reg {:reporters {:jmx {:enabled true}}}}}
   :webserver {:port 8180
               :host "0.0.0.0"}
   :web-router-service {:puppetlabs.trapperkeeper.services.metrics.metrics-service/metrics-webservice
                        "/metrics"}})

(deftest test-metrics-service-error
  (testing "Metrics service throws an error if missing server-id"
    (logging/with-test-logging
     (is (thrown-with-msg?
          ExceptionInfo
          #"Value does not match schema: .*server-id missing-required-key.*"
          (with-app-with-config
           app
           services
           (ks/dissoc-in metrics-service-config [:metrics :server-id])))))))

(deftest test-metrics-service
  (testing "Can boot metrics service and access registry"
    (with-app-with-config
     app
     services
     (assoc-in metrics-service-config [:metrics :metrics-webservice :mbeans :enabled] true)

     (testing "metrics service functions"
       (let [svc (app/get-service app :MetricsService)]
         (testing "`get-metrics-registry` called without domain works"
           (is (instance? MetricRegistry (metrics-protocol/get-metrics-registry svc))))

         (testing "`get-metrics-registry` called with domain works"
           (is (instance? MetricRegistry
                          (metrics-protocol/get-metrics-registry svc :pl.foo.reg))))

         (testing "`get-server-id` works"
           (is (= "localhost" (metrics-protocol/get-server-id svc))))))

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

      (testing "register should add a metric to the registry with a keyword domain"
        (let [svc (app/get-service app :MetricsService)
              register-and-get-metric (fn [domain metric]
                                        (metrics/register
                                          (metrics-protocol/get-metrics-registry svc domain)
                                          (metrics/host-metric-name "localhost" metric)
                                          (metrics/gauge 2))
                                        (http-client/get
                                          (str "http://localhost:8180/metrics/v1/mbeans/"
                                                 (codec/url-encode
                                                  (str (name domain) ":name=puppetlabs.localhost." metric)))))
              resp (register-and-get-metric :pl.test.reg "foo")]
          (is (= 200 (:status resp)))
          (is (= {"Value" 2} (parse-response resp)))))

      (testing "querying multiple metrics via POST should work"
        (let [svc (app/get-service app :MetricsService)
              registry (metrics-protocol/get-metrics-registry svc :pl.other.reg)]
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

(deftest metrics-v1-endpoint-disabled-by-default
  (testing "metrics/v1 is disabled by default, returns 404"
      (with-app-with-config
       app
       [jetty9-service/jetty9-service
        webrouting-service/webrouting-service
        metrics-service
        metrics-webservice]
       metrics-service-config
        (let [resp (http-client/get "http://localhost:8180/metrics/v1/mbeans")]
          (is (= 404 (:status resp)))))))

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
    (let [config (-> metrics-service-config
                     (assoc-in [:metrics :metrics-webservice :mbeans :enabled] true)
                     (assoc :registries
                            {:pl.no.jmx {:reporters
                                         {:jmx
                                          {:enabled false}}}}))]
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

(deftest get-metrics-registry-service-function-test
  (with-app-with-config
   app
   [metrics-service]
   {:metrics {:server-id "localhost"}}
   (let [svc (app/get-service app :MetricsService)]
     (testing "Can access default registry"
       (is (instance? MetricRegistry (metrics-protocol/get-metrics-registry svc))))
     (testing "Can access other registries registry"
       (is (instance? MetricRegistry (metrics-protocol/get-metrics-registry svc :my-domain)))))))

(deftest get-server-id-service-function-test
  (with-app-with-config
   app
   [metrics-service]
   {:metrics {:server-id "foo"}}
   (let [svc (app/get-service app :MetricsService)]
     (testing "Can access server-id"
       (is (= "foo" (metrics-protocol/get-server-id svc)))))))

(deftest update-registry-settings-service-function-test
  (testing "intialize-registry-settings adds settings for a registry"
    (let [service (trapperkeeper/service
                   [[:MetricsService update-registry-settings]]
                   (init [this context]
                         (update-registry-settings :foo.bar {:default-metrics-allowed ["foo.bar"]})
                         context))
          metrics-app (trapperkeeper/build-app [service metrics-service]
                                               {:metrics utils/test-config})
          metrics-svc (app/get-service metrics-app :MetricsService)]
      (try
        (app/init metrics-app)
        (is (= {:foo.bar {:default-metrics-allowed ["foo.bar"]}}
               @(:registry-settings (tk-services/service-context metrics-svc))))

        (finally
          (app/stop metrics-app)))))

  (testing "update-registry-settings throws an error if called outside `init`"
    (let [service (trapperkeeper/service
                   [[:MetricsService update-registry-settings]]
                   (start [this context]
                          (update-registry-settings :nope {:default-metrics-allowed ["fail"]})
                          context))
          metrics-app (trapperkeeper/build-app [service metrics-service]
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
    [metrics-service]
    {:metrics {:server-id "localhost"
               :reporters {:jmx {:enabled true}}}})
   (is (logged? #"Enabling JMX globally is deprecated; JMX can only be enabled per-registry."))))

(deftest jmx-works-test
  (with-app-with-config
   app
   [metrics-service]
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
                 [[:MetricsService get-metrics-registry update-registry-settings]]
                 (init
                  [this context]
                  (get-metrics-registry :graphite-enabled)

                  (get-metrics-registry :graphite-with-default-metrics-allowed)
                  (update-registry-settings :graphite-with-default-metrics-allowed
                                                default-metrics-allowed)

                  (get-metrics-registry :graphite-with-metrics-allowed)

                  ;; shouldn't matter whether `get-metrics-registry` or
                  ;; `update-registry-settings` is called first
                  (update-registry-settings :graphite-with-defaults-and-metrics-allowed
                                                default-metrics-allowed)
                  (get-metrics-registry :graphite-with-defaults-and-metrics-allowed)

                  context))
        reported-metrics-atom (atom {})]
    (with-redefs [core/build-graphite-sender
                  (fn [_ domain] (utils/make-graphite-sender reported-metrics-atom domain))]
      (let [metrics-app (trapperkeeper/build-app
                         [metrics-service service]
                         config)
            metrics-svc (app/get-service metrics-app :MetricsService)
            get-context (fn [] (tk-services/service-context metrics-svc))]
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
                (is (every? #(instance? GraphiteReporter %)
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
