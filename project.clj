(defproject todomvc "0.0.0"
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/clojurescript "1.9.89"]
                 [com.cerner/clara-rules "0.13.0"]
                 [reagent "0.6.0-rc"]
                 [re-frame "0.9.0"]
                 [binaryage/devtools "0.8.1"]
                 [secretary "1.2.3"]]

  :plugins [[lein-cljsbuild "1.1.4"]
            [lein-figwheel "0.5.6"]]

  :hooks [leiningen.cljsbuild]

  :clean-targets ^{:protect false} ["resources/public/js" "target"]

  :profiles
  {:dev
   {:dependencies [[binaryage/devtools "0.8.2"]
                   [figwheel-sidecar "0.5.9"]]
    :plugins      [[lein-figwheel "0.5.9"]]
    :source-paths ["src"]}}

  :cljsbuild
  {:builds
   [{:id           "dev"
     :source-paths ["src" "dev"]
     :figwheel     {:on-jsload "todomvc.core/main"}
     :compiler     {:asset-path           "js"
                    :optimizations        :none
                    :cache-analysis       false
                    :source-map           true
                    :source-map-timestamp true
                    :output-dir           "resources/public/js"
                    :output-to            "resources/public/js/client.js"
                    :main                 todomvc.core}}

    {:id           "prod"
     :source-paths ["src"]
     :compiler     {:optimizations :advanced
                    :elide-asserts true
                    :output-dir    "resources/public/js/min"
                    :output-to     "resources/public/js/min/client.js"
                    :pretty-print  false}}]})
