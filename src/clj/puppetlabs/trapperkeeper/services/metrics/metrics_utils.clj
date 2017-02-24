(ns puppetlabs.trapperkeeper.services.metrics.metrics-utils
  (:require [cheshire.custom :refer [JSONable]]
            [clojure.java.jmx :as jmx]
            [puppetlabs.kitchensink.core :as ks]))

(defn filter-mbean
  "Converts an mbean to a map. For attributes that can't be converted to JSON,
  return a string representation of the value."
  [mbean]
  {:post [(map? %)]}
  (->> mbean
       (ks/mapvals (fn [v]
                     (cond
                       ;; Nested structures should themselves be filtered
                       (map? v) (filter-mbean v)
                       (instance? java.util.HashMap v) (->> v
                                                            (into {})
                                                            filter-mbean)
                       (satisfies? JSONable v) v
                       :else (str v))))))

(defn all-mbean-names
  "Return a seq of all mbeans names"
  []
  {:post [(coll? %)]}
  (map str (jmx/mbean-names "*:*")))

(defn mbean-names
  "Return a map of mbean name to a link that will retrieve the attributes"
  []
  (->> (all-mbean-names)
       (map (fn [mbean-name]
              [mbean-name
               (format "/mbeans/%s" (java.net.URLEncoder/encode mbean-name "UTF-8"))]))
       (into (sorted-map))))

(defn get-mbean
  "Returns the attributes of a given MBean"
  [name]
  (when (some #(= name %) (all-mbean-names))
    (filter-mbean
     (jmx/mbean name))))
