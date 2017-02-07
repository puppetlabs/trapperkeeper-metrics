(defproject puppetlabs/trapperkeeper-metrics "0.6.1-SNAPSHOT"
  :description "Trapperkeeper Metrics Service"
  :url "http://github.com/puppetlabs/trapperkeeper-metrics"

  :min-lein-version "2.7.1"

  :pedantic? :abort

  :parent-project {:coords [puppetlabs/clj-parent "0.3.3"]
                   :inherit [:managed-dependencies]}

  :dependencies [[org.clojure/clojure]

                 [prismatic/schema]

                 [puppetlabs/kitchensink]
                 [puppetlabs/trapperkeeper]
                 [puppetlabs/ring-middleware]

                 [cheshire]
                 [org.clojure/java.jmx]

                 ;; ring-defaults brings in a bad, old version of the servlet-api, which
                 ;; now has a new artifact name (javax.servlet/javax.servlet-api).  If we
                 ;; don't exclude the old one here, they'll both be brought in, and consumers
                 ;; will be subject to the whims of which one shows up on the classpath first.
                 ;; thus, we need to use exclusions here, even though we'd normally resolve
                 ;; this type of thing by just specifying a fixed dependency version.
                 [ring/ring-defaults nil :exclusions [javax.servlet/servlet-api]]

                 [org.clojure/tools.logging]
                 [io.dropwizard.metrics/metrics-core "3.1.2"]
                 [io.dropwizard.metrics/metrics-graphite "3.1.2"]
                 [org.jolokia/jolokia-core "1.3.5"]
                 [puppetlabs/comidi]
                 [puppetlabs/i18n]]

  :plugins [[puppetlabs/i18n "0.6.0"]
            [lein-parent "0.3.1"]]

  :source-paths  ["src/clj"]
  :java-source-paths  ["src/java"]

  :deploy-repositories [["releases" {:url "https://clojars.org/repo"
                                     :username :env/clojars_jenkins_username
                                     :password :env/clojars_jenkins_password
                                     :sign-releases false}]]

  :profiles {:dev {:dependencies [[puppetlabs/http-client]
                                  [puppetlabs/trapperkeeper :classifier "test"]
                                  [puppetlabs/trapperkeeper-webserver-jetty9]
                                  [puppetlabs/kitchensink :classifier "test"]]}})
