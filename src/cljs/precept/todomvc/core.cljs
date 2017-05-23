(ns ^:figwheel-always precept.todomvc.core
  (:require-macros [secretary.core :refer [defroute]])
  (:require [goog.events :as events]
            [precept.state :as state]
            [precept.core :refer [start! then]]
            [precept.spec.sub :as sub]
            [precept.todomvc.views]
            [precept.todomvc.facts :refer [todo]]
            [precept.todomvc.schema :refer [app-schema]]
            [precept.todomvc.rules :refer [app-session]]
            [reagent.core :as reagent]
            [secretary.core :as secretary])
  (:import [goog History]
           [goog.history EventType]))

(enable-console-print!)

;; Instead of secretary consider:
;;   - https://github.com/DomKM/silk
;;   - https://github.com/juxt/bidi
(defroute "/" [] (then [:global :ui/visibility-filter :all]))

(defroute "/:filter" [filter] (then [:global :ui/visibility-filter (keyword filter)]))

(def history
  (doto (History.)
    (events/listen EventType.NAVIGATE
                   (fn [event] (secretary/dispatch! (.-token event))))
    (.setEnabled true)))

(defn mount-components []
  (reagent/render [precept.todomvc.views/todo-app] (.getElementById js/document "app")))

(def facts (into (todo "Hi") (todo "there!")))

(defn ^:export main []
  (println "Starting with session " app-session)
  (start! {:session app-session :facts facts}
    (fn [_]
      (println "Mounting!")
      (mount-components))))
