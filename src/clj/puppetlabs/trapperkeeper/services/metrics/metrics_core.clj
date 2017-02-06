(ns puppetlabs.trapperkeeper.services.metrics.metrics-core
  (:import (com.codahale.metrics JmxReporter MetricRegistry)
           (com.fasterxml.jackson.core JsonParseException))
  (:require [clojure.tools.logging :as log]
            [clojure.java.io :as io]
            [cheshire.core :as json]
            [schema.core :as schema]
            [ring.middleware.defaults :as ring-defaults]
            [puppetlabs.comidi :as comidi]
            [puppetlabs.ring-middleware.utils :as ringutils]
            [puppetlabs.trapperkeeper.services.metrics.metrics-utils
             :as metrics-utils]
            [puppetlabs.trapperkeeper.services.metrics.jolokia
             :as jolokia]
            [puppetlabs.kitchensink.core :as ks]
            [puppetlabs.i18n.core :as i18n :refer [trs tru]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Schemas

(def JmxReporterConfig
  {:enabled schema/Bool})

(def JolokiaApiConfig
  {(schema/optional-key :enabled) schema/Bool
   (schema/optional-key :servlet-init-params) jolokia/JolokiaConfig})

(def ReportersConfig
  {(schema/optional-key :jmx) JmxReporterConfig})

(def WebserviceConfig
  {(schema/optional-key :jolokia) JolokiaApiConfig})

(def MetricsConfig
  {:server-id                       schema/Str
   (schema/optional-key :enabled)   schema/Bool
   (schema/optional-key :reporters) ReportersConfig
   (schema/optional-key :metrics-webservice) WebserviceConfig})

(def RegistryContext
  {:registry (schema/maybe MetricRegistry)
   :jmx-reporter (schema/maybe JmxReporter)})

(def MetricsServiceContext
  {:registries (schema/atom {schema/Any RegistryContext})})

(def Keyword-or-Str (schema/if keyword? schema/Keyword schema/Str))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Private

(schema/defn jmx-reporter :- JmxReporter
  [registry :- MetricRegistry
   domain :- (schema/maybe schema/Keyword)]
  (let [b (JmxReporter/forRegistry registry)]
    (when domain
      (.inDomain b (name domain)))
    (.build b)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(schema/defn initialize :- RegistryContext
  [config :- MetricsConfig
   domain :- (schema/maybe Keyword-or-Str)]
  (let [domain (keyword domain)
        jmx-config (get-in config [:reporters :jmx])
        registry (MetricRegistry.)]
    (when (contains? config :enabled)
      (log/warn (format "%s  %s"
                        (trs "Metrics are now always enabled.")
                        (trs "To suppress this warning remove metrics.enabled from your configuration."))))
    {:registry registry
     :jmx-reporter (when (:enabled jmx-config)
                     (doto ^JmxReporter (jmx-reporter registry domain)
                       (.start)))}))

(schema/defn get-or-initialize! :- RegistryContext
  [config :- MetricsConfig
   {:keys [registries]} :- MetricsServiceContext
   domain :- Keyword-or-Str]
  (let [domain (keyword domain)]
    (if-let [metric-reg (get @registries domain)]
      metric-reg
      (let [reg-context (initialize config domain)]
        (swap! registries assoc domain reg-context)
        reg-context))))

(schema/defn stop :- RegistryContext
  [context :- RegistryContext]
  (if-let [jmx-reporter (:jmx-reporter context)]
    (.close jmx-reporter))
  context)


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Comidi

(defn build-handler [path]
  (comidi/routes->handler
   (comidi/wrap-routes
    (comidi/context path
        (comidi/context "/v1"
            (comidi/context "/mbeans"
                (comidi/GET "" []
                  (fn [req]
                    (ringutils/json-response 200
                                             (metrics-utils/mbean-names))))
              (comidi/POST "" []
                (fn [req]
                  (try
                    (let [metrics (with-open [reader (-> req :body io/reader)]
                                    (doall (json/parse-stream reader true)))]
                      (cond
                        (seq? metrics)
                        (ringutils/json-response
                         200 (map metrics-utils/get-mbean metrics))

                        (string? metrics)
                        (ringutils/json-response
                         200 (metrics-utils/get-mbean metrics))

                        (map? metrics)
                        (ringutils/json-response
                         200 (ks/mapvals metrics-utils/get-mbean metrics))

                        :else
                        (ringutils/json-response
                         400 (tru "metrics request must be a JSON array, string, or object"))))

                    (catch JsonParseException e
                      (ringutils/json-response 400 {:error (str e)})))))

              (comidi/GET ["/" [#".*" :names]] []
                (fn [{:keys [route-params] :as req}]
                  (let [name (java.net.URLDecoder/decode (:names route-params))]
                    (if-let [mbean (metrics-utils/get-mbean name)]
                      (ringutils/json-response 200 mbean)
                      (ringutils/json-response 404
                                               (tru "No mbean ''{0}'' found" name)))))))))
    (comp i18n/locale-negotiator #(ring-defaults/wrap-defaults % ring-defaults/api-defaults)))))
