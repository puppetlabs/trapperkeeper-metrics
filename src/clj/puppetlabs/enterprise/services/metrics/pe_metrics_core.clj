(ns puppetlabs.enterprise.services.metrics.pe-metrics-core
  (:import (java.net InetSocketAddress)
           (com.codahale.metrics MetricRegistry)
           (com.codahale.metrics.graphite Graphite GraphiteSender)
           (java.util.concurrent TimeUnit)
           (com.puppetlabs.enterprise PEMetricFilter PEGraphiteReporter))
  (:require [schema.core :as schema]
            [puppetlabs.trapperkeeper.services.metrics.metrics-core :as metrics-core]
            [puppetlabs.kitchensink.core :as ks]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Schemas

(def BaseGraphiteReporterConfig
  {:host schema/Str
   :port schema/Int
   :update-interval-seconds schema/Int})

(def GraphiteReporterConfig
  (assoc BaseGraphiteReporterConfig :enabled schema/Bool))

;; schema for what is read from config file for a registry
(def GraphiteRegistryReporterConfig
  (assoc (ks/mapkeys schema/optional-key BaseGraphiteReporterConfig)
    :enabled schema/Bool))

(def PERegistryReportersConfig
  (merge metrics-core/ReportersConfig
         {(schema/optional-key :graphite) GraphiteRegistryReporterConfig}))

(def PERegistryConfig
  {(schema/optional-key :metrics-allowed) [schema/Str]
   (schema/optional-key :metric-prefix) schema/Str
   (schema/optional-key :reporters) PERegistryReportersConfig})

(def PERegistriesConfig
  {schema/Any PERegistryConfig})

(def PEReportersConfig
  {(schema/optional-key :graphite) BaseGraphiteReporterConfig})

(def PEMetricsConfig
  (merge metrics-core/MetricsConfig
         {(schema/optional-key :registries) PERegistriesConfig
          (schema/optional-key :reporters) PEReportersConfig}))

(def PERegistryContext
  (merge metrics-core/RegistryContext
         {(schema/optional-key :graphite-reporter) PEGraphiteReporter}))

(def DefaultPERegistrySettings
  {:default-metrics-allowed [schema/Str]})

(def PEMetricsServiceContext
  {:registries (schema/atom {schema/Any PERegistryContext})
   :can-update-registry-settings? schema/Bool
   :registry-settings (schema/atom {schema/Any DefaultPERegistrySettings})
   :metrics-config PEMetricsConfig})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Private

(schema/defn construct-metric-names :- #{schema/Str}
  "Prefixes the metric prefix to each metric name. Returns a set of metric names (duplicates are
  removed)."
  [prefix :- schema/Str
   metric-names :- [schema/Str]]
  (set (map #(format "%s.%s" prefix %) metric-names)))

(schema/defn build-metric-filter :- PEMetricFilter
  [metrics-allowed :- #{schema/Str}]
  (PEMetricFilter. metrics-allowed))

(schema/defn get-metric-prefix :- schema/Str
  "Determines what the metric prefix should be.
  If a metric-prefix is set in the config, we use that. Else default to the server-id"
  [pe-metrics-config :- PEMetricsConfig
   domain :- schema/Keyword]
  (if-let [metric-prefix (get-in pe-metrics-config [:registries domain :metric-prefix])]
    metric-prefix
    (format "puppetlabs.%s" (:server-id pe-metrics-config))))

(schema/defn pe-config->oss-config :- metrics-core/MetricsConfig
  [config :- PEMetricsConfig
   domain :- schema/Keyword]
  (-> config
      ;; TODO: this is a hack to convert from pe-tk-metrics' config to tk-metrics'. In the future,
      ;; we hope to update tk-metrics to enable jmx per-registry and remove this hack. See TK-393.
      (assoc-in [:reporters :jmx :enabled]
                (get-in config [:registries domain :reporters :jmx :enabled] false))
      (dissoc :registries)
      (ks/dissoc-in [:reporters :graphite])))

(schema/defn pe-context->oss-context :- metrics-core/RegistryContext
  [context :- PERegistryContext]
  (dissoc context :graphite-reporter))

(schema/defn build-graphite-reporter :- PEGraphiteReporter
  "Constructs a PEGraphiteReporter instance for the given registry, with the given allowed metrics,
  and using the given graphite-sender"
  [registry :- MetricRegistry
   metrics-allowed :- #{schema/Str}
   graphite-sender :- GraphiteSender]
  (->
   (PEGraphiteReporter/forRegistry registry)
   (.convertRatesTo (TimeUnit/MILLISECONDS))
   (.convertDurationsTo (TimeUnit/MILLISECONDS))
   (.filter (build-metric-filter metrics-allowed))
   (.build graphite-sender)))

(schema/defn build-graphite-sender :- GraphiteSender
  [graphite-config :- GraphiteReporterConfig
   ;; The domain is only needed as an argument for testing, which is unfortunate. In the future, it
   ;; would be nice to add the ability to register a function that could receive a callback when a
   ;; reporter is added, which could solve the problem of needing this extra argument solely for
   ;; testing (see PE-17010).
   domain :- schema/Keyword]
  (Graphite. (InetSocketAddress. (:host graphite-config)
                                 (:port graphite-config))))

(schema/defn add-graphite-reporter :- PERegistryContext
  "Adds a graphite reporter to the given registry context if graphite
  is enabled in the configuration. Starts up a thread which reports the metrics
  to graphite on the interval specified in :update-interval-seconds"
  [registry-context :- metrics-core/RegistryContext
   graphite-config :- (schema/maybe GraphiteReporterConfig)
   metrics-allowed :- #{schema/Str}
   domain :- schema/Keyword]
  (if (:enabled graphite-config)
    (let [graphite-sender (build-graphite-sender graphite-config domain)
          graphite-reporter (build-graphite-reporter (:registry registry-context)
                                                     metrics-allowed
                                                     graphite-sender)]
      (.start graphite-reporter (:update-interval-seconds graphite-config) (TimeUnit/SECONDS))
      (assoc registry-context :graphite-reporter graphite-reporter))
    registry-context))

(schema/defn get-graphite-config :- (schema/maybe GraphiteReporterConfig)
  "Merge together the graphite config for the registry with the global graphite config."
  [config :- PEMetricsConfig
   domain :- schema/Keyword]
  (let [reporter-config (get-in config [:reporters :graphite])
        registry-config (get-in config [:registries domain :reporters :graphite])
        merged-config (merge reporter-config registry-config)]
    ;; the default value for enabled is false
    (if (nil? merged-config)
      merged-config
      (update-in merged-config [:enabled] #(if (nil? %) false %)))))

(schema/defn get-metrics-allowed :- #{schema/Str}
  "Get the metrics allowed for the registry. Looks at the metrics-allowed registered for the
  registry in the registry settings atom using the `initialize-registry-settings` function as well
  as the metrics-allowed listed in the config file under the `:metrics-allowed` key. Merges these
  lists together and then adds the metrics prefix to them, returning a set of prefixed allowed
  metrics."
  [pe-metrics-config :- PEMetricsConfig
   registry-settings :- {schema/Any DefaultPERegistrySettings}
   domain :- schema/Keyword]
  (let [metric-prefix (get-metric-prefix pe-metrics-config domain)
        default-metrics-allowed (get-in registry-settings [domain :default-metrics-allowed])
        configured-metrics-allowed (get-in pe-metrics-config [:registries domain :metrics-allowed])
        metrics-allowed (concat default-metrics-allowed configured-metrics-allowed)]
    (construct-metric-names metric-prefix metrics-allowed)))

(schema/defn initialize-registry-context :- metrics-core/RegistryContext
  "Create initial registry context. This will include a MetricsRegistry and a JMX reporter, but not
  a Graphite reporter."
  [pe-metrics-config :- PEMetricsConfig
   domain :- schema/Keyword]
  (let [oss-metrics-config (pe-config->oss-config pe-metrics-config domain)]
    (if (= domain :default)
      (metrics-core/initialize oss-metrics-config nil)
      (metrics-core/initialize oss-metrics-config (name domain)))))

(schema/defn maybe-add-default-to-config :- PEMetricsConfig
  "Add a `:default` key with an empty map as the value to the registries config if it is not
  present."
  [metrics-config :- PEMetricsConfig]
  (update-in metrics-config [:registries :default] #(if (nil? %) {} %)))

(schema/defn initialize-registries-from-config :- {schema/Any metrics-core/RegistryContext}
  "Read through the config and create a MetricsRegistry (+ JMX reporter if configured) for every
  registry mentioned in it. Also create the default registry if not mentioned in the config. Should
  be called from `init` of the pe-metrics-service."
  [metrics-config :- PEMetricsConfig]
  (into {} (map
            (fn [x] {x (initialize-registry-context metrics-config x)})
            (keys (:registries metrics-config)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(schema/defn ^:always-validate add-graphite-reporters :- PEMetricsServiceContext
  "Add Graphite reporters to all registries with Graphite enabled in the config, using the
  configured settings for each registry. Returns an updated service context. Should be called from
  `start` of the pe-metrics-service."
  [service-context :- PEMetricsServiceContext]
  (let [config (:metrics-config service-context)
        registry-settings @(:registry-settings service-context)]
    (doseq [registry @(:registries service-context)]
      (let [domain (key registry)
            graphite-config (get-graphite-config config domain)
            metrics-allowed (get-metrics-allowed config registry-settings domain)
            registry-with-graphite-reporter (add-graphite-reporter
                                             (val registry)
                                             graphite-config
                                             metrics-allowed
                                             domain)]
        (swap! (:registries service-context) assoc domain registry-with-graphite-reporter))))
  service-context)

;; Note here that the return schema includes registries that could have Graphite reporters. If the
;; registry was in the config, then a Graphite reporter could have been configured for it. Any
;; registries not in the config will not have Graphite reporters.
(schema/defn ^:always-validate get-or-initialize-registry-context :- PERegistryContext
  "If a registry exists within the service context for a given domain
  already, return it.
  Otherwise initialize a new registry for that domain and return it.
  Modifies the registries atom in the service context to add the new registry"
  [{:keys [registries metrics-config]} :- PEMetricsServiceContext
   domain :- schema/Keyword]
  (if-let [metric-registry-context (get @registries domain)]
    metric-registry-context
    (let [new-registry-context (initialize-registry-context metrics-config domain)]
      (swap! registries assoc domain new-registry-context)
      new-registry-context)))

(schema/defn ^:always-validate create-initial-service-context :- PEMetricsServiceContext
  "Create the initial service context for the pe-metrics-service. Initialize all registries in the
  config, add them to the `registries` atom, and include that in the service context map, along with
  an empty atom for `registry-settings` and the metrics config."
  [metrics-config :- PEMetricsConfig]
  (let [config-with-default (maybe-add-default-to-config metrics-config)
        registries (initialize-registries-from-config config-with-default)]
    {:registries (atom registries)
     :can-update-registry-settings? true
     :registry-settings (atom {})
     :metrics-config config-with-default}))

(schema/defn lock-registry-settings :- PEMetricsServiceContext
  "Switch the `can-update-registry-settings?` boolean to false to show that it is after the `init`
  phase and registry settings can no longer be set."
  [context :- PEMetricsServiceContext]
  (assoc context :can-update-registry-settings? false))

(schema/defn ^:always-validate initialize-registry-settings :- {schema/Any DefaultPERegistrySettings}
  "Update the `registry-settings` atom for the given domain. Can only be called once per-domain."
  [context :- PEMetricsServiceContext
   domain :- schema/Keyword
   settings :- DefaultPERegistrySettings]
  (if (= false (:can-update-registry-settings? context))
    (throw (RuntimeException.
            "Registry settings must be initialized in the `init` phase of the lifecycle."))
    (let [registry-settings (:registry-settings context)]
      (if (get @registry-settings domain)
        (throw (RuntimeException.
                (format "Registry %s has already had settings initialized; can't call more than once."
                        domain)))
        (swap! registry-settings assoc domain settings)))))

(schema/defn ^:always-validate stop
  [context :- PERegistryContext]
  (metrics-core/stop (pe-context->oss-context context))
  (if-let [graphite-reporter (:graphite-reporter context)]
    (.close graphite-reporter)))

(schema/defn ^:always-validate stop-all
  [service-context :- PEMetricsServiceContext]
  (let [registries (:registries service-context)]
    (doseq [[_ metrics-registry] @registries]
      (stop metrics-registry))
    service-context))
