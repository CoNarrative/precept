(ns libx.todomvc.rules-debug
  (:require [clara.rules.accumulators :as acc]
            [clara.rules :as cr]
            [libx.spec.sub :as sub]
            [libx.todomvc.schema :refer [app-schema]]
            [libx.listeners :as l]
            [libx.util :refer [insert! insert-unconditional! retract! attr-ns guid]]
            #?(:clj [libx.tuplerules :refer [def-tuple-rule deflogical]])
            #?(:cljs [libx.tuplerules :refer-macros [def-tuple-session deflogical def-tuple-rule]])
            [libx.schema :as schema]
            [libx.util :as util]))

(defn trace [& args]
  (apply prn args))

(deflogical [?e :entry/new-title "Good morning Vietnam!"] :- [[?e :entry/title]])
(deflogical [?e :entry/new-title "Hello again!"] :- [[?e :entry/title]])


(def-tuple-rule all-facts
  {:group :report}
  [?facts <- (acc/all) :from [:all]]
  =>
  (println "FACTs at the end" ?facts))

(def-tuple-rule action-cleanup
  {:group :cleanup}
  ;[?action <- [_ :action]]
  [?actions <- (acc/all) :from [:action]]
  [:test (> (count ?actions) 0)]
  =>
  (trace "CLEANING actions" ?actions)
  (doseq [action ?actions]
    (cr/retract! action)))

(cr/defrule remove-older-unique-identity-facts
  {:super true :salience 100}
  [:unique-identity (= ?a (:a this)) (= ?t1 (:t this))]
  [?fact2 <- :unique-identity (= ?a (:a this)) (= ?t2 (:t this))]
  [:test (> ?t1 ?t2)]
  =>
  (trace (str "SCHEMA MAINT - :unique-identity retracting") ?fact2)
  (retract! ?fact2))

(cr/defrule remove-older-unique-value-facts
  {:super true :salience 100}
  [:unique-value (= ?e (:e this)) (= ?a (:a this)) (= ?t1 (:t this))]
  [?fact2 <- :unique-value (= ?e (:e this)) (= ?a (:a this)) (= ?t2 (:t this))]
  [:test (> ?t1 ?t2)]
  =>
  (trace (str "SCHEMA MAINT - removing :unique-value " ?a) ?fact2)
  (retract! ?fact2))

(def groups [:action :calc :report :cleanup])
(def activation-group-fn (util/make-activation-group-fn :calc))
(def activation-group-sort-fn (util/make-activation-group-sort-fn groups :calc))
(def hierarchy (schema/schema->hierarchy app-schema))
(def ancestors-fn (util/make-ancestors-fn hierarchy))

(cr/defsession app-session
  'libx.todomvc.rules-debug
  :fact-type-fn :a
  :ancestors-fn ancestors-fn
  :activation-group-fn activation-group-fn
  :activation-group-sort-fn activation-group-sort-fn)

(-> app-session
  (l/replace-listener)
  (util/insert [(guid) :entry/foo-action :tag])
  (util/insert [[(guid) :entry/title "Hello."]
                [(guid) :entry/title "Hello???"]
                [1 :todo/title "H"]
                [1 :todo/title "Hi"]])
  (cr/fire-rules)
  (l/vec-ops))
