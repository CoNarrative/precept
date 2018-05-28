(ns ^:figwheel-no-load precept.app
  (:require
    [precept.app-ns :as app-ns]
    [devtools.core :as devtools]
    [figwheel.client :as figwheel :include-macros true]))

(enable-console-print!)
(devtools/install!)

(figwheel/watch-and-reload
  :on-jsload #(do (println "Loaded.")
                  (app-ns/main)))

(app-ns/main)
