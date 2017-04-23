(ns ^:figwheel-always libx.todomvc.core
  (:require-macros [secretary.core :refer [defroute]])
  (:require [goog.events :as events]
            [libx.core :refer [start! then state store]]
            [libx.util :refer [insert insert-fire]]
            [clara.rules :as cr]
            [devtools.core :as devtools]
            [reagent.core :as reagent]
            [secretary.core :as secretary]
            [libx.spec.sub :as sub]
            [libx.todomvc.views]
            [libx.todomvc.schema :refer [app-schema]]
            [libx.todomvc.rules :refer [app-session #_find-all-facts]]
            [libx.util :as util])
  (:import [goog History]
           [goog.history EventType]))

(enable-console-print!)

;; Instead of secretary consider:
;;   - https://github.com/DomKM/silk
;;   - https://github.com/juxt/bidi
(defroute "/" [] (then [(random-uuid) :ui/visibility-filter :all]))

(defroute "/:filter" [filter]
  (then [(random-uuid) :ui/visibility-filter (keyword filter)]))

(def history
  (doto (History.)
    (events/listen EventType.NAVIGATE
                   (fn [event] (secretary/dispatch! (.-token event))))
    (.setEnabled true)))

(defn mount-components []
  (reagent/render [libx.todomvc.views/todo-app] (.getElementById js/document "app")))

(def facts [[(random-uuid):todo/title "Hi"]])

(defn ^:export main []
    (start! {:session app-session :schema app-schema :facts facts})
    (mount-components))


;; 637 = molasses
;(count (:?facts (first (cr/query (:session @state) libx.todomvc.rules/find-all-facts))))
(:schema @state)
