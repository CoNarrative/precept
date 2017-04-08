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
            [libx.core :refer [router changes-ch registry]]
            [libx.util :refer [insert-fire]]
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

(defn init-router! []
  (let [r (router changes-ch registry)]
    (println "Router initialized" r)
    r))

(init-router!)

(def changes {:added [[-1 :done/count 1000]]})
(for [change (libx.core/embed-op changes)]
  (put! changes-ch change))

(defn ^:export main []
  (let [initial-state (-> app-session
                        (add-me/replace-listener)
                        (insert-fire (visibility-filter (random-uuid) :all)))
        changes (add-me/ops initial-state)
        _ (println "Initial state" changes)]
    (dispatch-sync [:initialise-db initial-state])
    (mount-components)))
