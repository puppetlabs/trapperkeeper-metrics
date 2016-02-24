(def ks-version "1.2.0")
(def tk-version "1.2.0")

(defproject puppetlabs/trapperkeeper-metrics "0.1.3-SNAPSHOT"
  :description "Trapperkeeper Metrics Service"
  :url "http://github.com/puppetlabs/trapperkeeper-metrics"

  :pedantic? :abort

  :dependencies [[org.clojure/clojure "1.7.0"]

                 ;; begin version conflict resolution dependencies
                 [clj-time "0.10.0"]
                 [commons-codec "1.9"]
                 [org.clojure/tools.macro "0.1.5"]
                 [commons-io "2.4"]
                 ;; end version conflict resolution dependencies

                 [puppetlabs/kitchensink ~ks-version]
                 [puppetlabs/trapperkeeper ~tk-version]

                 [ring/ring-core "1.4.0"]

                 [cheshire "5.3.1"]
                 [org.clojure/java.jmx "0.3.1"]
                 ;; ring-defaults brings in a bad, old version of the servlet-api, which
                 ;; now has a new artifact name (javax.servlet/javax.servlet-api).  If we
                 ;; don't exclude the old one here, they'll both be brought in, and consumers
                 ;; will be subject to the whims of which one shows up on the classpath first.
                 ;; thus, we need to use exclusions here, even though we'd normally resolve
                 ;; this type of thing by just specifying a fixed dependency version.
                 [ring/ring-defaults "0.1.5" :exclusions [javax.servlet/servlet-api]]

                 [org.clojure/tools.logging "0.2.6"]
                 [org.slf4j/slf4j-api "1.7.7"]
                 [io.dropwizard.metrics/metrics-core "3.1.2"]
                 [puppetlabs/comidi "0.3.1"]]

  :deploy-repositories [["releases" {:url "https://clojars.org/repo"
                                     :username :env/clojars_jenkins_username
                                     :password :env/clojars_jenkins_password
                                     :sign-releases false}]]

  :profiles {:dev {:dependencies [[puppetlabs/http-client "0.4.4" :exclusions [commons-io]]
                                  [puppetlabs/trapperkeeper ~tk-version :classifier "test"]
                                  [puppetlabs/trapperkeeper-webserver-jetty9 "1.3.1" :exclusions [clj-time]]
                                  [puppetlabs/kitchensink ~ks-version :classifier "test"]]}})
