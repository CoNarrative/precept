(ns ^:figwheel-always libx.todomvc.core
  (:require-macros [secretary.core :refer [defroute]])
  (:require [goog.events :as events]
            [libx.core :refer [start! then state store]]
            [libx.spec.sub :as sub]
            [libx.todomvc.views]
            [libx.todomvc.schema :refer [app-schema]]
            [libx.todomvc.rules :refer [app-session]]
            [reagent.core :as reagent]
            [secretary.core :as secretary])
  (:import [goog History]
           [goog.history EventType]))

(enable-console-print!)

;; Instead of secretary consider:
;;   - https://github.com/DomKM/silk
;;   - https://github.com/juxt/bidi
(defroute "/" [] (then :ui/set-visibility-filter-action
                       {:ui/visibility-filter :all}))

(defroute "/:filter" [filter] (then :ui/set-visibility-filter-action
                                    {:ui/visibility-filter (keyword filter)}))

(def history
  (doto (History.)
    (events/listen EventType.NAVIGATE
                   (fn [event] (secretary/dispatch! (.-token event))))
    (.setEnabled true)))

(defn client-coords [e]
  {:x (.-clientX e)
   :y (.-clientY e)})

(defn hit-node [event]
  "Takes .path property of a DOM event and returns first element with an id"
  (first (filter #(not (clojure.string/blank? (.-id %))) (.-path event))))

;(defn client-coords [e]
;  (let [rect (.getBoundingClientRect (.-target e))]
;    {:x (- (.-clientX e) (.-left rect))
;     :y (- (.-clientY e) (.-top rect))}))

(defn mount-components []
  (reagent/render [libx.todomvc.views/todo-app] (.getElementById js/document "app"))
  (.addEventListener js/window "mousedown" #(then :mouse/mouse-down-action {:node (hit-node %)}))
  (.addEventListener js/window "mousemove" #(then :mouse/mouse-move-action (client-coords %)))
  (.addEventListener js/window "mouseup" #(then :mouse/mouse-up-action (client-coords %))))

(defn todo [title]
  (let [id (random-uuid)]
    [[id :todo/title title]
     [id :todo/done false]]))

(def facts (into (todo "Hi") (todo "there!")))

(defn ^:export main []
    (start! {:session app-session :schema app-schema :facts facts})
    (mount-components))


;; 637 facts = molasses
@store