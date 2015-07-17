(ns puppetlabs.trapperkeeper.services.metrics.metrics-core
  (:import (com.codahale.metrics JmxReporter MetricRegistry))
  (:require [clojure.tools.logging :as log]
            [schema.core :as schema]
            [puppetlabs.kitchensink.core :as ks]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Schemas

(def JmxReporterConfig
  {:enabled schema/Bool})

(def ReportersConfig
  {(schema/optional-key :jmx) JmxReporterConfig})

(def MetricsConfig
  {:server-id                       schema/Str
   (schema/optional-key :enabled)   schema/Bool
   (schema/optional-key :reporters) ReportersConfig})

(def MetricsServiceContext
  {:registry (schema/maybe MetricRegistry)
   (schema/optional-key :jmx-reporter) JmxReporter})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Private

(defn jmx-reporter
  [registry jmx-config]
  (doto (-> (JmxReporter/forRegistry registry)
          (.build))
    (.start)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(schema/defn initialize :- MetricsServiceContext
  [config :- MetricsConfig]
  (let [enabled-given   (contains? config :enabled)
        jmx-config      (get-in config [:reporters :jmx])]
    (when enabled-given
      (log/warn "Metrics are now always enabled.  To suppress this warning remove metrics.enabled from your configuration."))
    (let [registry (MetricRegistry.)]
      (cond-> {:registry registry}

        (:enabled jmx-config)
        (assoc :jmx-reporter (jmx-reporter registry jmx-config))))))

(schema/defn stop :- MetricsServiceContext
  [context :- MetricsServiceContext]
  (if-let [jmx-reporter (:jmx-reporter context)]
    (.close jmx-reporter))
  context)
