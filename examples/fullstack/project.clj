(defproject fullstack "0.0.0"
  :dependencies [[org.clojure/clojure "1.9.0-alpha15"]
                 [bouncer "1.0.0"]
                 [ch.qos.logback/logback-classic "1.1.7"]
                 [cljs-ajax "0.5.8"]
                 [compojure "1.5.2"]
                 [cprop "0.1.10"]
                 [hiccup "1.0.5"]
                 [luminus-http-kit "0.1.4"]
                 [luminus-nrepl "0.1.4"]
                 [luminus/ring-ttl-session "0.3.1"]
                 [metosin/compojure-api "1.1.10"]
                 [metosin/ring-http-response "0.8.1"]
                 [mount "0.1.11"]
                 [org.clojure/clojurescript "1.9.473" :scope "provided"]
                 [org.clojure/core.async "0.3.442"]
                 [org.clojure/tools.cli "0.3.5"]
                 [org.clojure/tools.logging "0.3.1"]
                 [precept "0.3.1-alpha"]
                 [reagent "0.6.0"]
                 [reagent-utils "0.2.0"]
                 [ring-middleware-format "0.7.2"]
                 [ring/ring-core "1.5.1"]
                 [ring/ring-defaults "0.2.3"]
                 [secretary "1.2.3"]
                 [selmer "1.10.6"]
                 [com.taoensso/sente "1.11.0"]]

  :jvm-opts ["-server" "-Dconf=.lein-env"]

  :source-paths ["src/clj" "src/cljc"]

  :test-paths ["test"]

  :resource-paths ["resources" "target/cljsbuild"]

  :target-path "target/%s/"

  :main fullstack.core

  :plugins [[lein-cprop "1.0.1"]
            [lein-cljsbuild "1.1.4"]]

  :clean-targets ^{:protect false}

  [:target-path [:cljsbuild :builds :app :compiler :output-dir] [:cljsbuild :builds :app :compiler :output-to]]

  :figwheel
  {:http-server-root "public"
   :nrepl-port 7002
   :css-dirs ["resources/public/css"]
   :nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]}

  :profiles
  {:dev           [:project/dev :profiles/dev]
   :test          [:project/dev :project/test :profiles/test]

   :project/dev  {:dependencies [[prone "1.1.4"]
                                 [ring/ring-mock "0.3.0"]
                                 [ring/ring-devel "1.5.1"]
                                 [pjstadig/humane-test-output "0.8.1"]
                                 [binaryage/devtools "0.9.0"]
                                 [com.cemerick/piggieback "0.2.2-SNAPSHOT"]
                                 [doo "0.1.7"]
                                 [org.clojure/test.check "0.9.0"]
                                 [figwheel-sidecar "0.5.10-SNAPSHOT"]]
                  :plugins      [[com.jakemccrary/lein-test-refresh "0.18.1"]
                                 [lein-doo "0.1.7"]
                                 [lein-figwheel "0.5.10-SNAPSHOT"]
                                 [org.clojure/clojurescript "1.9.473"]]
                  :cljsbuild
                               {:builds
                                {:app
                                 {:source-paths ["src/cljs" "src/cljc" "env/dev/cljs"]
                                  :compiler
                                                {:main "fullstack.app"
                                                 :asset-path "/js/out"
                                                 :output-to "target/cljsbuild/public/js/app.js"
                                                 :output-dir "target/cljsbuild/public/js/out"
                                                 :source-map true
                                                 :optimizations :none
                                                 :pretty-print true}}}}



                  :doo {:build "test"}
                  :source-paths ["env/dev/clj"]
                  :resource-paths ["env/dev/resources"]
                  :repl-options {:init-ns user}
                  :injections [(require 'pjstadig.humane-test-output)
                               (pjstadig.humane-test-output/activate!)]}

   :project/test {:resource-paths ["env/test/resources"]
                  :cljsbuild
                                 {:builds
                                  {:test
                                   {:source-paths ["src/cljs" "test/cljs"]
                                    :compiler
                                                  {:output-to "target/test.js"
                                                   :main "fullstack.doo-runner"
                                                   :optimizations :whitespace
                                                   :pretty-print true}}}}}


   :profiles/dev {}
   :profiles/test {}})
