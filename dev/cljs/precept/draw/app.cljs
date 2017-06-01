(ns ^:figwheel-no-load precept.draw.app
  (:require [precept.draw.core :as core]
            [figwheel.client :as figwheel :include-macros true]))

(figwheel/watch-and-reload
  :on-jsload core/mount-components)

(core/main)
