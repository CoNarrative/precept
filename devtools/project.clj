(defproject precept-devtools "0.0.0"
  :description "Precept dev tools"
  :url          "https://github.com/CoNarrative/precept.git"
  :license      {:name "MIT"
                 :url "https://github.com/CoNarrative/precept/blob/master/LICENSE"}
  :dependencies [[org.clojure/clojure "1.9.0-alpha17"]
                 [org.clojure/spec.alpha "0.1.109"]
                 [org.clojure/clojurescript "1.9.562"]
                 [precept "0.3.2-alpha"]
                 [reagent "0.6.0"]]

  :plugins [[lein-cljsbuild "1.1.4"]]

  :source-paths ["src/clj" "src/cljc" "src/cljs"]

  :test-paths ["test/clj" "test/cljc"]

  :resource-paths ["resources" "target/cljsbuild"]

  :figwheel
  {:http-server-root "public"
   :nrepl-port 7002
   :reload-clj-files {:clj true :cljc true}
   :nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]}

  :profiles
  {:dev
   {:dependencies [[com.cemerick/piggieback "0.2.2-SNAPSHOT"]
                   [figwheel-sidecar "0.5.10-SNAPSHOT"]]

    :plugins      [[lein-figwheel "0.5.10-SNAPSHOT"]]

    :repl-options {:init-ns user}

    :source-paths ["dev/clj"]

    :cljsbuild
    {:builds
     {:dev
       {:source-paths ["dev" "src/cljs" "src/cljc"]
        :compiler
                     {:main "precept-devtools.app"
                      :output-to "target/cljsbuild/public/js/app.js"
                      :output-dir "target/cljsbuild/public/js/out"
                      :asset-path "/js/out"
                      :optimizations :none
                      :cache-analysis false
                      :source-map true
                      :pretty-print true}}}}

    :deploy-repositories [["releases"  {:sign-releases false
                                        :url "https://clojars.org/repo"}]
                          ["snapshots" {:sign-releases false
                                        :url "https://clojars.org/repo"}]]}})
