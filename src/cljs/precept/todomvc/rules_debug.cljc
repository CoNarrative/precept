;(ns precept.todomvc.rules-debug
;  (:require [clara.rules.accumulators :as acc]
;            [clara.rules :as cr]
;            [precept.spec.sub :as sub]
;            [precept.listeners :as l]
;            [precept.schema :as schema]
;            [precept.util :refer [insert! insert-unconditional! retract! guid] :as util]
;            #?(:clj [precept.tuplerules :refer [def-tuple-session
;                                                def-tuple-rule
;                                                deflogical
;                                                defsub
;                                                store-action]])
;            #?(:cljs [precept.tuplerules :refer-macros [deflogical
;                                                        defsub
;                                                        store-action
;                                                        def-tuple-session
;                                                        def-tuple-rule]])))
;  ;#?(:cljs (:require-macros [precept.dsl :refer [<- entity]])))
;
;(defn trace [& args]
;  (apply prn args))
;
;(def-tuple-rule handle-action
;  {:group :action}
;  [[_ :entry/foo-action ?v]]
;  =>
;  (insert-unconditional! [[(guid) :foo/id (:foo/id ?v)]
;                          [(guid) :foo/name (:foo/name ?v)]]))
;
;(deflogical [?e :entry/new-title "Hello again!"] :- [[?e :entry/title]])
;
;(def-tuple-rule all-facts
;  {:group :report}
;  [?facts <- (acc/all) :from [:all]]
;  =>
;  (println "FACTs at the end!" ?facts))
;
;(def-tuple-rule print-entity
;  [[?e :todo/title]]
;  [(<- ?entity (entity ?e))]
;  =>
;  (println "Entity!" ?entity))
;
;(defsub :my-sub
;  [?name <- [_ :foo/name]]
;  =>
;  (let [my-var "x"]
;    (println "Heyo")
;    {:foo/name ?name}))
;
;(def-tuple-session app-session
;   'precept.todomvc.rules-debug
;   :schema schema/precept-schema)
;
;(-> app-session
;  (l/replace-listener)
;  (util/insert [[1 :entry/title "First"]
;                [1 :entry/title "Second"]
;                [2 :todo/title "First"]
;                [3 ::sub/request :my-sub]
;                [:transient :test "foo"]
;                [2 :todo/title "Second"]])
;  (util/insert-action [(guid) :entry/foo-action {:foo/id 2 :foo/name "bar"}])
;  (cr/fire-rules)
;  (l/vec-ops))
;
