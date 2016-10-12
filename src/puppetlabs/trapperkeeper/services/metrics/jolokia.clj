(ns puppetlabs.trapperkeeper.services.metrics.jolokia
  (:require [ring.util.servlet :as ring-servlet]
            [clojure.tools.logging :as log])
  (:import (javax.servlet.http HttpServletRequest)
           (org.jolokia.http AgentServlet)
           (org.jolokia.util LogHandler)))

(defn jolokia-agent-servlet
  "Creates a servlet that will do an authorization check using a ring handler,
  and then (if the request is authorized) calls into the Jolokia AgentServlet."
  [auth-check-fn]
  (let [check-auth (fn [^HttpServletRequest request]
                     (auth-check-fn
                      (ring-servlet/build-request-map request)))]
    (proxy [AgentServlet] []
      (createLogHandler [_ _]
        (reify
          LogHandler
          (debug [this message]
            (log/debug message))
          (info [this message] (log/info message))
          (error [this message throwable] (log/error throwable message))))
      (service [request response]
        (let [{:keys [authorized message]} (check-auth request)]
          (if-not authorized
            (ring-servlet/update-servlet-response
             response
             {:status 403
              :headers {}
              :body message})
            (proxy-super service request response)))))))
