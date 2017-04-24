(defproject libx "0.1.0"
  :url          "https://github.com/CoNarrative/libx.git"
  :license      {:name "Eclipse Public License The Same As Clojure"
                 :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.9.0-alpha15"]
                 [org.clojure/clojurescript "1.9.494"]
                 [org.clojure/core.async "0.3.442"]
                 [com.cerner/clara-rules "0.14.0"]
                 [reagent "0.6.0"]]

  :plugins [[lein-cljsbuild "1.1.4"]
            [lein-codox "0.10.3"]]

  :source-paths ["src/clj"]

  :test-paths ["test/clj"]

  :resource-paths ["resources" "target/cljsbuild"]

  :figwheel
  {:http-server-root "public"
   :nrepl-port 7002
   :nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]}

  :profiles
  {:dev
   {:dependencies [[org.clojure/test.check "0.9.0"]
                   [org.clojure/tools.namespace "0.2.11"]
                   [devcards "0.2.3"]
                   [com.cemerick/piggieback "0.2.2-SNAPSHOT"]
                   [figwheel-sidecar "0.5.10-SNAPSHOT"]
                   [binaryage/devtools "0.8.2"]
                   [secretary "1.2.3"]]

    :plugins      [[lein-figwheel "0.5.10-SNAPSHOT"]
                   [lein-doo "0.1.7"]]

    :doo {:paths {:karma "./node_modules/karma/bin/karma"}}

    :cljsbuild
    {:builds
     {:app
      {:source-paths ["src/cljs" "dev/cljs"]
       :compiler
                     {:main "libx.todomvc.app"
                      :asset-path "/js/out"
                      :output-to "target/cljsbuild/public/js/app.js"
                      :output-dir "target/cljsbuild/public/js/out"
                      :preloads [devtools.preload]
                      :source-map true
                      :optimizations :none
                      :cache-analysis false
                      :pretty-print true}}

      :test
       {:source-paths ["src/cljs" "test/cljs"]
        :compiler
                     {:main "libx.runner"
                      :output-to "target/cljsbuild/public/js/test/test.js"
                      :output-dir "target/cljsbuild/public/js/test/out"
                      :asset-path "target/cljsbuild/public/js/test/out"
                      :optimizations :none
                      :source-map true
                      :pretty-print true}}

      :devcards-test
       {:source-paths ["src/cljs" "test/cljs"]
        :compiler
                      {:main "libx.runner"
                       :output-to "target/cljsbuild/public/js/devcards/main.js"
                       :output-dir "target/cljsbuild/public/js/devcards/out"
                       ;:asset-path "target/cljsbuild/public/js/devcards/out"
                       :asset-path "/js/out"
                       :preloads [devtools.preload]
                       :optimizations :none
                       :devcards true
                       :source-map true
                       :pretty-print true}}}}

    :repl-options {:init-ns user}
    :source-paths ["dev/clj"]}})
