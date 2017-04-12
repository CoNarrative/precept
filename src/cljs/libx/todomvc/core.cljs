(ns libx.todomvc.core
  (:require-macros [secretary.core :refer [defroute]])
  (:require [goog.events :as events]
            [devtools.core :as devtools]
            [reagent.core :as reagent]
            [re-frame.core :refer [dispatch dispatch-sync]]
            [secretary.core :as secretary]
            [cljs.core.async :refer [put!]]
            [libx.todomvc.events]
            [libx.todomvc.subs]
            [libx.todomvc.views]
            [libx.todomvc.rules :refer [app-session]]
            [libx.todomvc.facts :refer [visibility-filter]]
            [libx.core :refer [start!]]
            [libx.util :refer [insert insert-fire]]
            [libx.todomvc.add-me :as add-me])
  (:import [goog History]
           [goog.history EventType]))

(enable-console-print!)

;; Instead of secretary consider:
;;   - https://github.com/DomKM/silk
;;   - https://github.com/juxt/bidi
(defroute "/" [] (dispatch [:set-showing :all]))
(defroute "/:filter" [filter] (dispatch [:set-showing (keyword filter)]))
(def history
  (doto (History.)
    (events/listen EventType.NAVIGATE
                   (fn [event] (secretary/dispatch! (.-token event))))
    (.setEnabled true)))

(defn mount-components []
  (reagent/render [libx.todomvc.views/todo-app] (.getElementById js/document "app")))

(defn ^:export main []
  (let [initial-state (-> app-session (add-me/replace-listener)
                                      (insert (visibility-filter (random-uuid) :all)))]
    ;(start! {:session initial-state})
    (mount-components)))
