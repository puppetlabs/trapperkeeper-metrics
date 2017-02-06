;; Utility functions for working with the Metrics library

(ns puppetlabs.metrics
  (:import (com.codahale.metrics MetricRegistry RatioGauge RatioGauge$Ratio
                                 Gauge Metric Metered Sampling Timer)
           (java.util.concurrent TimeUnit))
  (:require [schema.core :as schema]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(schema/defn ^:always-validate host-metric-name :- schema/Str
  "Given a hostname and a metric name, build a qualified metric name for use with
  Metrics."
  [hostname :- schema/Str
   metric-name :- schema/Str]
  (MetricRegistry/name "puppetlabs" (into-array String [hostname metric-name])))

(schema/defn ^:always-validate http-metric-name :- schema/Str
  "Given a hostname and a metric name, build a qualified http metric name for use
  with Metrics."
  [hostname :- schema/Str
   metric-name :- schema/Str]
  (MetricRegistry/name "puppetlabs" (into-array String [hostname "http" metric-name])))

(schema/defn ^:always-validate register :- Metric
  "Register a metric with a metrics registry, using the given metric name."
  [registry :- MetricRegistry
   metric-name :- schema/Str
   metric :- Metric]
  (.register registry metric-name metric))

(schema/defn mean :- Double
  "Given a Timer or Histogram object, get the current mean value."
  [sampling :- Sampling]
  (.. sampling getSnapshot getMean))

(schema/defn mean-millis :- Long
  "Given a Timer or Histogram object, get the mean sample time in milliseconds."
  [sampling :- Sampling]
  (.toMillis TimeUnit/NANOSECONDS (mean sampling)))

(schema/defn mean-in-unit :- Long
  "Given a Timer or Histogram object, get the mean sample time in the specified time unit."
  [sampling :- Sampling
   time-unit :- TimeUnit]
  (.convert time-unit (mean sampling) TimeUnit/NANOSECONDS))

(schema/defn ^:always-validate ratio :- RatioGauge
  "Given two functions, return a Ratio metric whose value will be computed
  by calling the first function to retrieve the numerator and the second
  function to retrieve the denominator"
  [numerator-fn :- (schema/pred ifn?)
   denominator-fn :- (schema/pred ifn?)]
  (proxy [RatioGauge] []
    (getRatio []
      (RatioGauge$Ratio/of
        (numerator-fn)
        (denominator-fn)))))

(schema/defn ^:always-validate metered-ratio :- RatioGauge
  "Given two Metered metrics, construct a Ratio metric whose numerator and denominator
  are computed by calling the `getCount` method of the Metered metrics."
  [numerator :- Metered
   denominator :- Metered]
  (ratio #(.getCount numerator) #(.getCount denominator)))

(schema/defn ^:always-validate gauge :- Gauge
  "Returns a Gauge metric with an initial value"
  [value]
  (proxy [Gauge] []
    (getValue []
      value)))

(defmacro time!
  "Times the body forms against the given Timer metric"
  [^Timer t & body]
  `(.time ~(vary-meta t assoc :tag `Timer)
     (proxy [Callable] []
       (call [] (do ~@body)))))
