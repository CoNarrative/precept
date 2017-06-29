(defproject precept "0.3.1-alpha"
  :description "A declarative programming framework"
  :url          "https://github.com/CoNarrative/precept.git"
  :license      {:name "MIT"
                 :url "https://github.com/CoNarrative/precept/blob/master/LICENSE"}
  :dependencies [[org.clojure/clojure "1.9.0-alpha15"]
                 [org.clojure/clojurescript "1.9.494"]
                 [org.clojure/core.async "0.3.442"]
                 [com.cerner/clara-rules "0.15.0"]
                 [reagent "0.6.0"]]

  :plugins [[lein-cljsbuild "1.1.4"]
            [lein-codox "0.10.3"]]

  :codox {:namespaces [precept.accumulators precept.core precept.dsl precept.listeners
                       precept.macros precept.query precept.rules precept.schema
                       precept.state precept.util
                       precept.spec.lang precept.spec.sub precept.spec.error]
          :output-path "docs"
          :metadata {:doc/format :markdown}}

  :source-paths ["src/clj" "src/cljc"]

  :test-paths ["test/clj" "test/cljc"]

  :resource-paths ["resources" "target/cljsbuild"]

  :figwheel
  {:http-server-root "public"
   :nrepl-port 7002
   :nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]}

  :profiles
  {:dev
   {:dependencies [[org.clojure/test.check "0.9.0"]
                   [org.clojure/tools.reader "1.0.0-beta4"]
                   [org.clojure/tools.namespace "0.2.11"]
                   [devcards "0.2.3"]
                   [com.cemerick/piggieback "0.2.2-SNAPSHOT"]
                   [figwheel-sidecar "0.5.10-SNAPSHOT"]
                   [binaryage/devtools "0.8.2"]]

    :plugins      [[lein-figwheel "0.5.10-SNAPSHOT"]
                   [lein-doo "0.1.7"]]

    :repl-options {:init-ns user}

    :source-paths ["dev/clj"]

    :doo {:paths {:karma "./node_modules/karma/bin/karma"}}

    :cljsbuild
    {:builds
     {:test
       {:source-paths ["src/cljs" "test/cljs" "test/cljc"]
        :compiler
                     {:main "precept.runner"
                      :output-to "target/cljsbuild/public/js/test/test.js"
                      :output-dir "target/cljsbuild/public/js/test/out"
                      :asset-path "/js/test/out"
                      :optimizations :none
                      :cache-analysis false
                      :source-map true
                      :pretty-print true}}

      :macros
      {:source-paths ["test/macros/clj" "test/macros/cljs" "test/macros/cljc"]
       :compiler
                     {:main "precept.app"
                      :output-to "target/cljsbuild/public/js/macros/macros.js"
                      :output-dir "target/cljsbuild/public/js/macros/out"
                      :asset-path "/js/macros/out"
                      ;:verbose true
                      :optimizations :none
                      :cache-analysis false
                      :source-map true
                      :pretty-print true}}

      :devcards-test
       {:source-paths ["src/cljs" "test/cljs"]
        :compiler
                      {:main "precept.runner"
                       :output-to "target/cljsbuild/public/js/devcards/main.js"
                       :output-dir "target/cljsbuild/public/js/devcards/out"
                       :asset-path "/js/out"
                       :preloads [devtools.preload]
                       :optimizations :none
                       :cache-analysis false
                       :devcards true
                       :source-map true
                       :pretty-print true}}}}

    :deploy-repositories [["releases"  {:sign-releases false
                                        :url "https://clojars.org/repo"}]
                          ["snapshots" {:sign-releases false
                                        :url "https://clojars.org/repo"}]]}})