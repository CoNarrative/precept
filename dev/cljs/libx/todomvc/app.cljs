(ns ^:figwheel-no-load libx.todomvc.app
  (:require [libx.todomvc.core :as core]
            [figwheel.client :as figwheel :include-macros true]))

(figwheel/watch-and-reload
  :on-jsload core/mount-components)

(core/main)
