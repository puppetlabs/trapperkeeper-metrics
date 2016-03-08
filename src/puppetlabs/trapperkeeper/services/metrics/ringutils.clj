(ns puppetlabs.trapperkeeper.services.metrics.ringutils
  (:require [cheshire.core :as json]
            [ring.util.response :as response]))

(defn json-response [status body]
  (-> body
      (json/generate-string {:pretty true})
      response/response
      (response/status status)
      (response/content-type "application/json; charset=utf-8")))
