;(ns precept.todomvc.rules-debug
;    #?(:cljs (:require-macros [precept.dsl :refer [<- entity]]))
;    (:require [clara.rules.accumulators :as acc]
;              [clara.rules :as cr]
;              [precept.spec.sub :as sub]
;              [precept.spec.rulegen :as rulegen]
;              [precept.spec.error :as err]
;              [precept.listeners :as l]
;              [precept.schema :as schema]
;              [precept.state :as state]
;              [precept.todomvc.schema :refer [db-schema]]
;              [precept.util :refer [insert! insert-unconditional! retract! guid] :as util]
;      #?(:clj
;              [precept.dsl :refer [<- entity entities]])
;      #?(:clj
;              [precept.rules :refer [session
;                                          rule
;                                          define
;                                          defsub]])
;      #?(:cljs [precept.rules :refer-macros [define
;                                                  defsub
;                                                  session
;                                                  rule]])))
;
;(defn trace [& args]
;  (apply prn args))
;
;(define [?e :entry/new-title "Hello again!"] :- [[?e :entry/title]])
;
;(rule all-facts
;  {:group :report}
;  [?facts <- (acc/all) :from [:all]]
;  =>
;  (do nil))
;  ;(println "FACTs at the end!" ?facts))
;
;(rule print-entity
;  [[?e :todo/title]]
;  [(<- ?entity (entity ?e))]
;  =>
;  (println "Entity!" ?entity))
;
;(defsub :my-sub
;  [?name <- [_ :foo/name]]
;  =>
;  (let [my-var "x"]
;    (println "Heyo" my-var)
;    {:foo/name ?name}))
;
;(defsub :my-sub-2
;  [?eids <- (acc/all :e) :from [:interesting-fact]] ;; Maybe new special form `(eids [:interesting-fact])`
;  [(<- ?interesting-entities (entities ?eids))]
;  =>
;  (let [_ (println "Sub with entities -----" ?interesting-entities)]
;    {:entities-sub ?interesting-entities}))
;
;(rule log-errors
;  [[?e ::err/type]]
;  [(<- ?error (entity ?e))]
;  =>
;  (println "Found error!" ?error))
;
;(defn foo? [x y] (not= x y))
;
;;(rule greater-than-42
;;  [[_ :interesting-fact (foo? ?v 42)]]
;;  =>
;;  (println "Greater than 42:" ?v))
;
;(rule find-43
;  [[_ :interesting-fact 43]]
;  =>
;  (insert! [(guid) :the-number 43]))
;
;(rule pred-w-bound-variable
;  [[_ :interesting-fact ?v2]]
;  [[_ :the-number (< ?v2 ?v1)]]
;  =>
;  (println "42s" ?v2))
;
;(cr/defrule greater-than-42
;  [:interesting-fact (= ?v (:v this)) (foo? ?v 42)]
;  =>
;  (println "Greater than 42:" ?v))
;
;(rule dynamic-type-tuple
;  [[_ :some-type-name ?attr]]
;  [?x <- (acc/all :e) :from [_ ?attr]]
;  [[_ ?attr]]
;  =>
;  (println "Attr" ?attr)
;  (println "eids" ?x))
;
;; https://github.com/cerner/clara-rules/issues/313
;(cr/defrule variable-binding-in-accumulator
;  [:the-keyword-e (= ?v (:v this))]
;  [?xs <- (acc/all ?v) :from [:interesting-fact]]
;  =>
;  (println "[variable-binding-in-acc] " ?xs))
;
;;(rule variable-binding-in-accumulator
;;  [[_ :the-keyword-e ?v]]
;;  [?xs <- (acc/all ?v) :from [_ :interesting-fact]]
;;  =>
;;  (println "[variable-binding-in-acc] " ?xs))
;(session app-session
;   'precept.todomvc.rules-debug
;   :db-schema db-schema)
;;(reset! precept.state/fact-index {})
;
;(-> app-session
;  (l/replace-listener)
;  (util/insert [[1 :entry/title "First"]
;                [1 :entry/title "Second"]
;                [2 :todo/title "First"]
;                [5 :some-type-name :interesting-fact]
;                [6 :the-keyword-e :e]
;                [3 ::sub/request :my-sub]
;                [:transient :test "foo"]
;                [1 :interesting-fact 42]
;                [2 :interesting-fact 42]
;                [3 :interesting-fact 42]
;                [4 :interesting-fact 42]
;                [4 :interesting-fact 43]
;                [2 :todo/title "Second"]
;                [3 :todo/title "Second"]
;                [5 ::sub/request :my-sub-2]])
;  (cr/fire-rules)
;  (l/vec-ops))
;
;(l/vec-ops app-session)
;
