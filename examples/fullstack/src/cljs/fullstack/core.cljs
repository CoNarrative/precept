(ns fullstack.core
  (:require [reagent.core :as r]
            [mount.core :as mount]
            [fullstack.rules :refer [app-session]]
            [fullstack.views :as views]
            [fullstack.ws :as ws]
            [precept.core :refer [start!]]
            [precept-devtools.core :as devtools]))


(defn mount-components []
  (r/render #'views/app (.getElementById js/document "app"))
  (devtools/render! {:rules precept.state/rules :store precept.state/store}))

(defn init! []
  (mount/start)
  (start! {:session app-session :facts [[:transient :start true]]})
  (devtools/render! {:rules precept.state/rules :store precept.state/store})
  (mount-components))
