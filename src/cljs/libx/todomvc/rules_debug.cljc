(ns libx.todomvc.rules-debug
  (:require [clara.rules.accumulators :as acc]
            [clara.rules :as cr]
            [libx.spec.sub :as sub]
            [libx.todomvc.schema :refer [app-schema]]
            [libx.listeners :as l]
            [libx.schema :as schema]
            [libx.util :refer [insert! insert-unconditional! retract! guid] :as util]
            #?(:clj [libx.tuplerules :refer [def-tuple-session def-tuple-rule deflogical
                                             store-action]])
            #?(:cljs [libx.tuplerules :refer-macros [deflogical store-action def-tuple-session
                                                     def-tuple-rule]])))

(defn trace [& args]
  (apply prn args))

(store-action :entry/foo-action)

(deflogical [?e :entry/new-title "Hello again!"] :- [[?e :entry/title]])

(def-tuple-rule all-facts
  {:group :report}
  [?facts <- (acc/all) :from [:all]]
  =>
  (println "FACTs at the end!" ?facts))

(def-tuple-rule action-cleanup
  {:group :cleanup}
  [?action <- [_ :action]]
  ;[?actions <- (acc/all) :from [:action]]
  ;[:test (> (count ?actions) 0)]
  =>
  (trace "CLEANING action" ?action)
  ;(doseq [action ?actions]
  (cr/retract! ?action))

(cr/defrule remove-older-one-to-one-facts
  {:super true :salience 100}
  [?fact1 <- :one-to-one (= ?e (:e this)) (= ?a (:a this)) (= ?t1 (:t this))]
  [?fact2 <- :one-to-one (= ?e (:e this)) (= ?a (:a this)) (= ?t2 (:t this))]
  [:test (> ?t1 ?t2)]
  =>
  (trace (str "SCHEMA MAINT - :one-to-one retracting") ?fact1 ?fact2)
  (retract! ?fact2))

(def groups [:action :calc :report :cleanup])
(def activation-group-fn (util/make-activation-group-fn :calc))
(def activation-group-sort-fn (util/make-activation-group-sort-fn groups :calc))
(def hierarchy (schema/schema->hierarchy app-schema))
(def ancestors-fn (util/make-ancestors-fn hierarchy))

;(cr/defsession app-session
;  'libx.todomvc.rules-debug
;  :fact-type-fn :a
;  :ancestors-fn ancestors-fn
;  :activation-group-fn activation-group-fn
;  :activation-group-sort-fn activation-group-sort-fn)

(def-tuple-session app-session
  'libx.todomvc.rules-debug
  :ancestors-fn ancestors-fn
  :activation-group-fn activation-group-fn
  :activation-group-sort-fn activation-group-sort-fn)


(-> app-session
  (l/replace-listener)
  (util/insert [[1 :entry/title "First"]
                [1 :entry/title "Second"]
                [2 :todo/title "First"]
                [2 :todo/title "Second"]])
  (util/insert-action [(guid) :entry/foo-action {:foo/id 2 :foo/name "bar"}])
  (cr/fire-rules)
  (l/vec-ops))

