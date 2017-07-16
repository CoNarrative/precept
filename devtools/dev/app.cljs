(ns ^:figwheel-no-load precept-devtools.app
  (:require
    [precept-devtools.core :as core]
    [figwheel.client :as figwheel :include-macros true]))

(enable-console-print!)

(figwheel/watch-and-reload
  :load-warninged-code true
  :on-jsload #(do (println "Loaded.")
                  (core/render!)))

(core/render!)

