(ns ^:figwheel-always story.core
  (:require-macros [secretary.core :refer [defroute]])
  (:require [goog.events :as events]
            [libx.state :as state]
            [libx.core :refer [start! then then-tuples]]
            [libx.spec.sub :as sub]
            [story.views]
            [story.schema :refer [app-schema]]
            [story.rules :refer [app-session]]
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


(defn hit-node [{event :e}]
  "Takes .path property of a DOM event and returns first element with an id"
  (first (filter #(not (clojure.string/blank? (.-id %))) (.-path event))))

(defn mount-components []
  (reagent/render [story.views/part-app] (.getElementById js/document "app"))
  (.addEventListener js/window "mousedown"
                     #(then-tuples {
                                    :action/type :mouse/down
                                    :mouse-down/target-id (hit-node %)
                                    :pos/x (.-clientX %)
                                    :pos/y (.-clientY %)})))

  ;(.addEventListener js/window "mousemove" #(then :mouse/move-action {:event %}))
  ;(.addEventListener js/window "mouseup" #(then :mouse/up-action {:event %})))

(defn part [title]
  (let [id (random-uuid)]
    [[id :part/title title]
     [id :part/done false]
     [id :dom/draggable? :tag]]))

(def facts (concat
             [[0 :mouse/op-mode :at-rest]]
             (part "what")
             (part "Hi")
             (part "there!")))

(println facts)
(defn ^:export main []
  (start! {:session app-session :schema app-schema :facts facts})
  (mount-components))

;@state/store

