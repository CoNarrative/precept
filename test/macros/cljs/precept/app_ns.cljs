(ns precept.app-ns
  (:require-macros [precept.dsl :refer [entity entities <-]])
  (:require [precept.rules :refer [defsub rule define session q defquery fire-rules]]
            [precept.util :as util :refer [insert!]]
            [reagent.core :as reagent]
            [precept.accumulators :as acc]
            [precept.core :as core]
            [precept.cljs-test-schema :refer [test-schema]]
            [precept.state :as state]))


(enable-console-print!)


(rule report-facts-at-start
  {:group :action}
  [?fact <- [_ :all]]
  =>
  (println "Fact at start>>>>>>>>>>>>>>>>"
    (with-out-str (cljs.pprint/pprint ((juxt :e :a :v :t) ?fact)))))

(rule report-facts-at-end
  {:group :report}
  [?facts <- (acc/all) :from [_ :all]]
  =>
  (println "<<<<<<<<<<<<<Facts at end"
    (with-out-str (cljs.pprint/pprint (mapv (juxt :e :a :v :t) ?facts)))))

(rule global-greater-than-500
  [?my-fact <- [:global :random-number ?v]]
  [:test (> ?v 500)]
  =>
  (insert! [:report :global-greater-than-500 true]))


;(defquery everything []
;  [?facts <- (acc/all) :from [_ :all]])


(rule less-than-500
  ;[[?e :random-number (number? ?v)]]
  ;[[?e :random-number (< ?v 500)]]
  ;[:or [:not [?e :greater-than-500]]
  ;     [:and [?e :random-number (number? ?v)]
  ;           [?e :random-number (< ?v 500)]]]


  [:and [?e :random-number ?v]
        [:not [?e :greater-than-500]]
        [:not [:global :random-number ?v]]]

  =>
  (insert! [?e :less-than-500 true]))


;(rule greater-than-500
;  [[?e :random-number (> ?v 500)]]
;  =>
;  (insert! [?e :greater-than-500 true]))

(define [?e :greater-than-500 true] :- [[?e :random-number (> ?v 500)]])

;(rule all-eids-greater-than-500
;  [?eids <- (acc/all :e) :from [_ :greater-than-500]]
;  =>
;  (util/insert! [:report :eids>500 ?eids]))

(rule entities-with-greater-than-500
  [[?e :greater-than-500]]
  [(<- ?fact (entity ?e))]
  =>
  (insert! [:report :entity>500 ?fact]))


(defsub :entities>500
  [?entities <- (acc/all (juxt :a :v)) :from [_ :entity>500]]
  =>
  {:facts ?entities})


(session my-session
  'precept.app-ns
  :reload true
  :db-schema test-schema)


(precept.core/start! {:session  my-session
                      :facts    [[:global :start true]]
                      :devtools true})

(defn view []
  (let [s @(precept.core/subscribe [:entities>500])
        _ (println "sub is" s)]
    [:div "Hello!"
     [:button {:on-click #(precept.core/then [(util/guid) :random-number (rand-int 1000)])}
      "Insert random random number fact"]
     [:button {:on-click #(precept.core/then [:global :random-number (rand-int 1000)])}
      "Insert `:global` random number fact"]]))

(defn main []
  (enable-console-print!)
  (reagent.core/render #'view (.getElementById js/document "app")))
