(ns precept.app-ns
  (:require [precept.rules :refer [rule define session q defquery fire-rules]]
            [precept.util :as util]
            [reagent.core :as reagent]
            [precept.accumulators :as acc]
            [precept.core :as core]
            [precept.cljs-test-schema :refer [test-schema]]
            [precept.state :as state]))


(enable-console-print!)

;(rule hello-world
;  [?fact <- [_ :foo]]
;  =>
;  (.log js/console "1" ?fact)
;  (util/insert! [1 :duplicate-fact-error 1]))
;

;(rule next-rule
;  [?fact <- [_ :duplicate-fact-error]]
;  =>
;  (.log js/console "3 ---------- " ?fact)
;  (util/insert! [1 :xyz 1]))

(rule report-facts-at-start
  {:group :action}
  [?fact <- [_ :all]]
  =>
  (println "<<<<<<<<<<<<<Fact at start>>>>>>>>>>>>>>>>" ?fact))

(rule report-facts-at-end
  {:group :report}
  [?facts <- (acc/all) :from [_ :all]]
  =>
  (println "All facts" ?facts))

(defquery everything []
  [?facts <- (acc/all) :from [_ :all]])

(define [?e :fact "falsee"] :- [[?e :foo]])

(session my-session
  'precept.app-ns
  :reload true
  :db-schema test-schema)

;(precept.rules/q (:session @precept.state/state) everything)

;@state/fact-index
;(println "Sessions")
;(cljs.pprint/pprint @state/session-defs)
;(println "Rules")
;(cljs.pprint/pprint @precept.state/rules)
;@precept.state/unconditional-inserts
;(cljs.pprint/pprint @precept.state/store)
;(vary-meta my-session assoc :hey "there")

(precept.core/start! {:session my-session
                      :facts [[:transient :foo "bar"]]
                      :devtools true})

;@precept.state/session-defs
;test-schema

(defn view []
  [:div "Hello world"
   [:button {:on-click #(precept.core/then [:global :foo (rand-int 1000)])}
    "Insert fact"]])

(defn main []
  (enable-console-print!)
  (reagent.core/render #'view (.getElementById js/document "app")))
