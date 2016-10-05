(ns puppetlabs.jolokia
  "Clojure interface to the Jolokia library"
  (:import [org.jolokia.util LogHandler]
           [org.jolokia.config ConfigKey Configuration]
           [org.jolokia.restrictor RestrictorFactory]
           [org.jolokia.backend BackendManager]
           [org.jolokia.http HttpRequestHandler])
  (:require [clojure.tools.logging :as log]
            [clojure.set :as setutils]
            [schema.core :as schema]))

(def config-mapping
  "Inspects the Jolokia ConfigKey Enum and generates a mapping that associates
  a Clojure keyword with each entry. For example, `ConfigKey/AGENT_ID` is
  associated with `:agent-id`

  For a complete list of configuration options, see:

    https://github.com/rhuss/jolokia/blob/v1.3.4/agent/core/src/main/java/org/jolokia/config/ConfigKey.java"
  (into {}
        (map
         #(vector
           (-> %
               .name
               .toLowerCase
               (.replace "_" "-")
               keyword)
           %)
         (ConfigKey/values))))

(def JolokiaConfig
  "Schema for validating Clojure maps containing Jolokia configuration.

  Creates a map of optional keys which have string values using the
  config-mapping extracted from the ConfigKey enum."
  (into {}
        (map
         #(vector (schema/optional-key %) schema/Str)
         (keys config-mapping))))

(def JolokiaConfigKey
  "Schema for validating Clojure Keywords that map to Jolokia configuration."
  (apply schema/enum (keys config-mapping)))

(def config-defaults
  "Default configuration values for Jolokia agents."
  ;; NOTE: each agent should be created with a unique :agent-id.
  {:agent-id "trapperkeeper-metrics"
   ;; Enables info logged at debug level.
   :debug "true"
   ;; Don't inclue backtraces in error results returned by the API.
   :include-stacktrace "false"
   ;; Load access policy from: resources/jolokia-access.xml
   :policy-location "classpath:/jolokia-access.xml"})

;; Implementation of the Jolokia logging interface that uses the standard
;; Clojure logger.
(defn create-logger []
  (reify
    LogHandler
    (debug [this message] (log/debug message))
    (info [this message] (log/info message))
    (error [this message throwable] (log/error throwable message))))

(defn create-config
  "Generate a Jolokia Configuration object from a Clojure map"
  ([]
   (create-config {}))

  ([config]
   (let [configuration (merge config-defaults config)]
     ;; Validate here to ensure defaults are also conformant.
     (schema/validate JolokiaConfig configuration)
     ;; The call to rename-keys uses the config-mapping to translate Clojure
     ;; keywords into entries in the org.jolokia.config.ConfigKey enum.
     (Configuration. (->> (setutils/rename-keys configuration config-mapping)
                          seq
                          flatten
                          (into-array Object))))))

(schema/defn ^:always-validate get-config
  "Looks up a value in a Jolokia Configuration object using a Clojure keyword."
  [config kwd :- JolokiaConfigKey]
  (.get config (get config-mapping kwd)))

(defn create-restrictor [config logger]
  ;; Resulting restrictor is configured according to the contents of
  ;; :policy-location in the config.
  (RestrictorFactory/createRestrictor config logger))

(defn create-backend [config logger restrictor]
  (BackendManager. config logger restrictor))

(defn create-handler
  [config backend logger]
  (HttpRequestHandler. config backend logger))
