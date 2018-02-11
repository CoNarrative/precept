(defproject precept-todomvc "0.0.0"
  :description "todomvc example"
  :url          "https://github.com/CoNarrative/precept.git"
  :license      {:name "MIT"
                 :url "https://github.com/CoNarrative/precept/blob/master/LICENSE"}
  :dependencies [[org.clojure/clojure "1.9.0-alpha17"]
                 [org.clojure/clojurescript "1.9.671"]
                 [precept "0.5.0-alpha"]
                 [reagent "0.6.0"]
                 [secretary "1.2.3"]]

  :plugins [[lein-cljsbuild "1.1.4"]]

  :source-paths ["src"]

  :resource-paths ["resources" "target/cljsbuild"]

  :figwheel
  {:http-server-root "public"
   :nrepl-port 7002
   :nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]}

  :profiles
  {:dev
   {:dependencies [[com.cemerick/piggieback "0.2.2-SNAPSHOT"]
                   [figwheel-sidecar "0.5.11"]
                   [binaryage/devtools "0.8.2"]]

    :plugins      [[lein-figwheel "0.5.11"]]

    :repl-options {:init-ns user}

    :source-paths ["dev/clj"]

    :cljsbuild
                  {:builds
                   {:app
                    {:source-paths ["src" "dev"]
                     :compiler
                                   {:main "todomvc.app"
                                    :asset-path "/js/out"
                                    :output-to "target/cljsbuild/public/js/app.js"
                                    :output-dir "target/cljsbuild/public/js/out"
                                    :preloads [devtools.preload]
                                    :source-map true
                                    :optimizations :none
                                    :cache-analysis false
                                    :pretty-print true}}}}}})
