(def ks-version "1.1.0")
(def tk-version "1.1.1")

(defproject puppetlabs/trapperkeeper-metrics "0.1.2-SNAPSHOT"
  :description "Trapperkeeper Metrics Service"
  :url "http://github.com/puppetlabs/trapperkeeper-metrics"

  :pedantic? :abort

  :dependencies [[org.clojure/clojure "1.6.0"]
                 [clj-time "0.7.0"]
                 [commons-io "2.4"]

                 [puppetlabs/kitchensink "1.1.0"]
                 [puppetlabs/trapperkeeper ~tk-version]

                 [org.clojure/tools.logging "0.2.6"]
                 [org.slf4j/slf4j-api "1.7.7"]
                 [metrics-clojure "2.5.1"]]

  :deploy-repositories [["releases" {:url "https://clojars.org/repo"
                                     :username :env/clojars_jenkins_username
                                     :password :env/clojars_jenkins_password
                                     :sign-releases false}]]


  :profiles {:dev {:dependencies [[puppetlabs/trapperkeeper ~tk-version :classifier "test"]
                                  [puppetlabs/kitchensink ~ks-version :classifier "test"]]}})
