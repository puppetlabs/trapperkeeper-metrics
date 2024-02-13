(ns puppetlabs.trapperkeeper.services.metrics.metrics-testutils
  (:import (com.codahale.metrics.graphite GraphiteSender))
  (:require [schema.core :as schema]
            [clojure.set]))

(def graphite-config
  {:graphite
   {:host "my.graphite.server"
    :port 2003
    :update-interval-seconds 10}})

(def test-config
  {:server-id "localhost"
   :reporters graphite-config})

(defn build-config-with-registries
  [registries]
  (assoc test-config :registries registries))

(schema/defn make-graphite-sender :- GraphiteSender
  "Creates a GraphiteSender object which doesn't make any network requests,
  but instead collects the data from each call to send into the provided atom"
  [reported-metrics :- (schema/atom {schema/Keyword #{schema/Str}})
   domain :- schema/Keyword]
  (reify GraphiteSender
    (connect [this])
    (send [this name value timestamp]
      (swap! reported-metrics #(update-in % [domain] clojure.set/union #{name})))
    (flush [this])
    (isConnected [this] true)
    (getFailures [this] 0)
    (close [this])))

(schema/defn reported?
  "Returns true if the given metric-name is found in reported-metrics"
  ([reported-metrics metric-name]
   (reported? reported-metrics :default metric-name))
  ([reported-metrics :- {schema/Keyword #{schema/Str}}
    domain :- schema/Keyword
    metric-name :- schema/Str]
   (contains? (get reported-metrics domain) metric-name)))

