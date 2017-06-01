(ns ^:figwheel-always precept.draw.core
  (:require [precept.core :refer [start! then]]
            [precept.util :refer [guid]]
            [precept.draw.facts :refer [todo visibility-filter]]
            [precept.draw.rules :refer [app-session]]
            [precept.draw.schema :refer [db-schema]]))

(enable-console-print!)

(defn circle [{:keys [container x y r]}]
  (let [eid (guid)]
    [[eid :elem/tag :svg:circle]
     [eid :attr/id eid]
     [eid :attr/cx x]
     [eid :attr/cy y]
     [eid :attr/r r]
     [container :contains eid]
     [:transient :command :create-element]]))

(def facts (circle {:container "root" :x 50 :y 50 :r 100}))

(defn mouse-move [e]
  [[:transient :mouse/x (.-clientX e)]
   [:transient :mouse/y (.-clientY e)]])

(defn mouse-down [e]
  [:transient :mouse/down e])

(defn mouse-up [e]
  [:transient :mouse/up e])

(defn key-down [e]
  [:transient :key-down/key-code (-> e .-target .-value)])

(defn ^:export main []
  (start! {:session app-session :facts facts})
  (doto js/window
    (.addEventListener "mousemove" #(then (mouse-move %)))
    (.addEventListener "mousedown" #(then (mouse-down %)))
    (.addEventListener "mouseup" #(then (mouse-up %)))
    (.addEventListener "keydown" #(then (key-down %)))))
