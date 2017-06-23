(ns ^:figwheel-no-load todomvc.app
  (:require [todomvc.core :as core]
            [figwheel.client :as figwheel :include-macros true]))

(figwheel/watch-and-reload
  :on-jsload core/mount-components)

(core/main)
