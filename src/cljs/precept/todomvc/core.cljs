(ns ^:figwheel-always precept.todomvc.core
  (:require-macros [secretary.core :refer [defroute]])
  (:require [goog.events :as events]
            [reagent.core :as reagent]
            [secretary.core :as secretary]
            [precept.core :refer [start! then]]
            [precept.todomvc.facts :refer [todo visibility-filter]]
            [precept.todomvc.rules :refer [app-session]]
            [precept.todomvc.schema :refer [app-schema]]
            [precept.todomvc.views])
  (:import [goog History]
           [goog.history EventType]))

(enable-console-print!)

(defroute "/" [] (then (visibility-filter :all)))

(defroute "/:filter" [filter] (then (visibility-filter (keyword filter))))

(def history
  (doto (History.)
    (events/listen EventType.NAVIGATE (fn [event] (secretary/dispatch! (.-token event))))
    (.setEnabled true)))

(defn mount-components []
  (reagent/render [precept.todomvc.views/app] (.getElementById js/document "app")))

(def facts (into (todo "Hi") (todo "there!")))

(defn ^:export main []
  (start! {:session app-session :facts facts})
  (mount-components))
