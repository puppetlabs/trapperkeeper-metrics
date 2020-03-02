(defproject puppetlabs/trapperkeeper-metrics "1.2.1"
  :description "Trapperkeeper Metrics Service"
  :url "http://github.com/puppetlabs/trapperkeeper-metrics"

  :min-lein-version "2.7.1"

  :pedantic? :abort

  :parent-project {:coords [puppetlabs/clj-parent "1.7.26"]
                   :inherit [:managed-dependencies]}

  :dependencies [[org.clojure/clojure]

                 [prismatic/schema]

                 [puppetlabs/kitchensink]
                 [puppetlabs/trapperkeeper]
                 [puppetlabs/ring-middleware]

                 [cheshire]
                 [org.clojure/java.jmx]

                 [ring/ring-defaults]

                 [org.clojure/tools.logging]
                 [io.dropwizard.metrics/metrics-core]
                 [io.dropwizard.metrics/metrics-graphite]
                 [org.jolokia/jolokia-core "1.6.2"]
                 [puppetlabs/comidi]
                 [puppetlabs/i18n]]

  :plugins [[puppetlabs/i18n "0.6.0"]
            [lein-parent "0.3.1"]]

  :source-paths  ["src/clj"]
  :java-source-paths  ["src/java"]

  :deploy-repositories [["releases" {:url "https://artifactory.delivery.puppetlabs.net/artifactory/clojure-releases__local/"
                                     :username :env/nexus_jenkins_username
                                     :password :env/nexus_jenkins_password
                                     :sign-releases false}]]

  :classifiers  [["test" :testutils]]

  :profiles {:dev {:aliases {"ring-example"
                             ["trampoline" "run"
                              "-b" "./examples/ring_app/bootstrap.cfg"
                              "-c" "./examples/ring_app/ring-example.conf"]}
                   :source-paths ["examples/ring_app/src"]
                   :dependencies [[puppetlabs/http-client]
                                  [puppetlabs/trapperkeeper :classifier "test"]
                                  [puppetlabs/trapperkeeper-webserver-jetty9]
                                  [puppetlabs/kitchensink :classifier "test"]]}
             :testutils {:source-paths ^:replace ["test"]
                         :java-source-paths ^:replace []}}

  :repl-options {:init-ns examples.ring-app.repl}

  :main puppetlabs.trapperkeeper.main)
