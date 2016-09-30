(ns puppetlabs.jolokia
  "Clojure interface to the Jolokia library"
  (:import [org.jolokia.util LogHandler]
           [org.jolokia.config ConfigKey Configuration]
           [org.jolokia.restrictor RestrictorFactory]
           [org.jolokia.backend BackendManager]
           [org.jolokia.http HttpRequestHandler])
  (:require [clojure.tools.logging :as log]))

;; Implementation of the Jolokia logging interface that uses the standard
;; Clojure logger.
(defn create-logger []
  (reify
    LogHandler
    (debug [this message] (log/debug message))
    (info [this message] (log/info message))
    (error [this message throwable] (log/error throwable message))))

(defn create-config []
  (new Configuration
       ;; For configuration options, see:
       ;;
       ;;   https://github.com/rhuss/jolokia/blob/v1.3.4/agent/core/src/main/java/org/jolokia/config/ConfigKey.java
       ;;
       ;; FIXME: AGENT_ID should be a unique string for each instance.
       (into-array Object [ConfigKey/AGENT_ID "jolokia-agent"
                           ;; FIXME: AGENT_CONTEXT should be the actual route
                           ;; that the handler will be mounted at.
                           ConfigKey/AGENT_CONTEXT "/metrics/v2"
                           ;; Enables info logged at debug level.
                           ConfigKey/DEBUG "true"
                           ;; Don't inclue backtraces in error results returned by the API.
                           ConfigKey/INCLUDE_STACKTRACE "false"
                           ;; Load access policy from: resources/jolokia-access.xml
                           ConfigKey/POLICY_LOCATION "classpath:/jolokia-access.xml"])))

(defn create-restrictor [config logger]
  ;; Resulting restrictor is configured according to the contents of POLICY_LOCATION
  (RestrictorFactory/createRestrictor config logger))

(defn create-backend [config logger restrictor]
  (BackendManager. config logger restrictor))

(defn create-handler []
  (let [config (create-config)
        logger (create-logger)
        restrictor (create-restrictor config logger)
        backend (create-backend config logger restrictor)]
    (new HttpRequestHandler config backend logger)))
