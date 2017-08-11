(ns fullstack.core
  (:require [reagent.core :as r]
            [mount.core :as mount]
            [fullstack.rules :refer [app-session]]
            [fullstack.views :as views]
            [fullstack.ws :as ws]
            [precept.core :refer [start!]]))


(defn mount-components []
  (r/render #'views/app (.getElementById js/document "app")))

(defn init! []
  (mount/start)
  (start! {:session app-session
           :facts [[:transient :start true]]
           :devtools true})
  (mount-components))
