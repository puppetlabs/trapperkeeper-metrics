(defproject puppetlabs/trapperkeeper-metrics "2.0.2"
  :description "Trapperkeeper Metrics Service"
  :url "http://github.com/puppetlabs/trapperkeeper-metrics"

  :min-lein-version "2.9.1"

  :pedantic? :abort

  :parent-project {:coords [puppetlabs/clj-parent "7.3.6"]
                   :inherit [:managed-dependencies]}

  :dependencies [[org.clojure/clojure]

                 [prismatic/schema]

                 [puppetlabs/kitchensink]
                 [puppetlabs/trapperkeeper]
                 [puppetlabs/trapperkeeper-authorization]
                 [puppetlabs/ring-middleware]

                 [cheshire]
                 [org.clojure/java.jmx]

                 [org.clojure/tools.logging]
                 [io.dropwizard.metrics/metrics-core]
                 [io.dropwizard.metrics/metrics-graphite]
                 [org.jolokia/jolokia-core "1.7.0"]
                 [puppetlabs/comidi]
                 [puppetlabs/i18n]]

  :plugins [[puppetlabs/i18n "0.9.2"]
            [lein-parent "0.3.9"]]

  :source-paths  ["src/clj"]
  :java-source-paths  ["src/java"]

  :deploy-repositories [["releases" {:url "https://clojars.org/repo"
                                     :username :env/clojars_jenkins_username
                                     :password :env/clojars_jenkins_password
                                     :sign-releases false}]]

  :classifiers  [["test" :testutils]]

  :profiles {:defaults {:dependencies [[puppetlabs/http-client]
                                       [puppetlabs/trapperkeeper :classifier "test"]
                                       [com.puppetlabs/trapperkeeper-webserver-jetty10]
                                       [puppetlabs/kitchensink :classifier "test"]]
                        :resource-paths ["dev-resources"]}
             :dev-dependencies {:dependencies [[org.bouncycastle/bcpkix-jdk18on]]}
             :dev [:defaults :dev-dependencies]
             :fips-dependencies {:dependencies [[org.bouncycastle/bcpkix-fips]
                                                [org.bouncycastle/bc-fips]
                                                [org.bouncycastle/bctls-fips]]
                                 :jvm-opts ~(let [version (System/getProperty "java.specification.version")
                                                  [major minor _] (clojure.string/split version #"\.")
                                                  unsupported-ex (ex-info "Unsupported major Java version. Expects 8, 11 or 17."
                                                                               {:major major
                                                                                :minor minor})]
                                                 (condp = (java.lang.Integer/parseInt major)
                                                   1 (if (= 8 (java.lang.Integer/parseInt minor))
                                                       ["-Djava.security.properties==./dev-resources/java.security.jdk8-fips"]
                                                       (throw unsupported-ex))
                                                   11 ["-Djava.security.properties==./dev-resources/java.security.jdk11-fips"]
                                                   17 ["-Djava.security.properties==./dev-resources/java.security.jdk17-fips"]
                                                   (throw unsupported-ex)))}
             :fips [:defaults :fips-dependencies]


             ;; per https://github.com/technomancy/leiningen/issues/1907
             ;; the provided profile is necessary for lein jar / lein install
             :provided {:dependencies [[org.bouncycastle/bcpkix-jdk18on]]
                        :resource-paths ["dev-resources"]}

             :testutils {:source-paths ^:replace ["test"]
                         :java-source-paths ^:replace []}}

  :repl-options {:init-ns examples.ring-app.repl}

  :main puppetlabs.trapperkeeper.main)
