(defproject puppetlabs/trapperkeeper-metrics "0.4.3-SNAPSHOT"
  :description "Trapperkeeper Metrics Service"
  :url "http://github.com/puppetlabs/trapperkeeper-metrics"

  :min-lein-version "2.7.1"

  :pedantic? :abort

  :parent-project {:coords [puppetlabs/clj-parent "0.2.4"]
                   :inherit [:managed-dependencies]}

  :dependencies [[org.clojure/clojure]

                 [prismatic/schema]

                 [puppetlabs/kitchensink]
                 [puppetlabs/trapperkeeper]
                 [puppetlabs/ring-middleware]

                 [ring/ring-core]

                 [cheshire]
                 [org.clojure/java.jmx]
                 [ring/ring-defaults]
                 ;; Explicitly reference the correct servlet-api so that downstream
                 ;; projects will always get it
                 [javax.servlet/javax.servlet-api "3.1.0"]

                 [org.clojure/tools.logging]
                 [org.slf4j/slf4j-api]
                 [io.dropwizard.metrics/metrics-core "3.1.2"]
                 [puppetlabs/comidi]
                 [puppetlabs/i18n]]

  :plugins [[puppetlabs/i18n "0.4.3"]
            [lein-parent "0.3.1"]]


  :deploy-repositories [["releases" {:url "https://clojars.org/repo"
                                     :username :env/clojars_jenkins_username
                                     :password :env/clojars_jenkins_password
                                     :sign-releases false}]]

  :profiles {:dev {:dependencies [[puppetlabs/http-client]
                                  [puppetlabs/trapperkeeper :classifier "test"]
                                  [puppetlabs/trapperkeeper-webserver-jetty9]
                                  [puppetlabs/kitchensink :classifier "test"]]}})
