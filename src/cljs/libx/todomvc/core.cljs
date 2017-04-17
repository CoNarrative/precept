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
            [libx.todomvc.rules :refer [app-session find-all-facts]]
            [libx.util :as util])
  (:import [goog History]
           [goog.history EventType]))

(enable-console-print!)

;; Instead of secretary consider:
;;   - https://github.com/DomKM/silk
;;   - https://github.com/juxt/bidi
(defroute "/" [] (println "Hey"));;(then [(random-uuid) :ui/visibility-filter :all])))

(defroute "/:filter" [filter] (println "there"))
;;(then [(random-uuid) :ui/visibility-filter ; (keyword)
;                                                                                    filter)])
(def history
  (doto (History.)
    (events/listen EventType.NAVIGATE
                   (fn [event] (secretary/dispatch! (.-token event))))
    (.setEnabled true)))

(defn mount-components []
  (reagent/render [libx.todomvc.views/todo-app] (.getElementById js/document "app")))

(def todo-id (random-uuid))
(def facts [[todo-id :todo/visible :tag]
            [todo-id :todo/title "Hi"]
            [(random-uuid) :ui/visibility-filter :done]])

(defn ^:export main []
    (start! {:session app-session :schema app-schema :facts facts})
    (mount-components))

;(main)
;(cljs.pprint/pprint (:session @state))
;(cljs.pprint/pprint (:subscriptions @state))
;(cljs.pprint/pprint (:session-history @state))
(cljs.pprint/pprint (:pending-updates @state))

(keys @store)
(vals @store)
(def attr-first-store (group-by second (vals @store)))
attr-first-store
(select-keys attr-first-store [::sub/response])
;(mapv #(cr/query % find-all-facts) (:session-history @state))

;(cr/query (:session @state) find-all-facts)
;(select-keys (:subscriptions @state) (vector [:todo-app]))

;(util/entities-where (:session @state) ::sub/request)