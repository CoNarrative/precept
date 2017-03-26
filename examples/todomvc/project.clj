(defproject todomvc-example "0.0.0"
  :dependencies [[org.clojure/clojure "1.9.0-alpha15"]
                 [org.clojure/clojurescript "1.9.494"]
                 [org.clojure/core.async "0.3.442"
                  :exclusions [org.clojure/tools.reader org.clojure/core.async]]
                 [com.cerner/clara-rules "0.13.0"]
                 [org.toomuchcode/clara-tools "0.1.1"
                  :exclusions [org.toomuchcode/clara-rules]]
                 [reagent "0.6.0"]
                 [re-frame "0.9.2"]
                 [binaryage/devtools "0.9.2"]
                 [secretary "1.2.3"]]

  :plugins [[lein-cljsbuild "1.1.4"]
            [lein-figwheel "0.5.9"
             :exclusions [org.clojure/clojure org.clojure/core.async]]]

  :clean-targets ^{:protect false} ["resources/public/js" "target"]

  :exclusions [org.clojure/core.async]

  :profiles
  {:dev
   {:dependencies [[binaryage/devtools "0.8.2"]
                   [figwheel-sidecar "0.5.9"
                    :exclusions [org.clojure/clojure org.clojure/core.async]]
                   [org.clojure/test.check "0.9.0"]]
    :plugins      [[lein-figwheel "0.5.9"
                    :exclusions [org.clojure/clojure org.clojure/core.async]]]
    :source-paths ["src/clj" "src/cljs" "dev"]}}

  :cljsbuild
  {:builds
   [{:id           "dev"
     :source-paths ["src/clj" "src/cljs" "dev"]
     :figwheel     {:on-jsload "libx.core/main"}
     :compiler     {:asset-path           "js"
                    :optimizations        :none
                    :cache-analysis       false
                    :source-map           true
                    :source-map-timestamp true
                    :output-dir           "resources/public/js"
                    :output-to            "resources/public/js/client.js"
                    :main                 todomvc.core}}
    ;{:id "test"
    ;         :source-paths ["src/libx/rules.cljs" "test"]
    ;         :compiler {:output-to "resources/public/js/testable.js"
    ;                    :main libx.runner
    ;                    :optimizations :none}}

    {:id           "prod"
     :source-paths ["src/clj" "src/cljs"]
     :compiler     {:optimizations :advanced
                    :elide-asserts true
                    :output-dir    "resources/public/js/min"
                    :output-to     "resources/public/js/min/client.js"
                    :pretty-print  false}}]})

   ;:test-commands {"test" ["lein" "doo" "phantom" "test" "once"]}})
