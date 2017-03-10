(ns examples.ring-app.ring-app
    (:require [clojure.tools.logging :as log]
              [puppetlabs.metrics :as metrics]
              [puppetlabs.trapperkeeper.core :refer [defservice]]
              [puppetlabs.trapperkeeper.services :refer [service-context]]))

(defservice count-service
  [[:MetricsService update-registry-settings get-metrics-registry get-server-id]
   [:WebserverService add-ring-handler]]
  (init [this context]
    (log/info "Count service starting up")
    (let [registry (get-metrics-registry :count-service)
          server-id (get-server-id)
          counter-to-report (.counter registry (metrics/host-metric-name
                                                 server-id
                                                 "count-service-report-me"))
          counter-to-not-report (.counter registry (metrics/host-metric-name
                                                    server-id
                                                    "count-service-dont-report-me"))]
      (add-ring-handler
       (fn [_]
         (.inc counter-to-report)
         (.inc counter-to-not-report)
         {:status 200
          :headers {"Content-Type" "text/plain"}
          :body (str "Requests made since startup: "
                     (.getCount counter-to-report))})
       "/count"))
    (update-registry-settings :count-service
                              {:default-metrics-allowed
                               ["count-service-report-me"]})

    context))
