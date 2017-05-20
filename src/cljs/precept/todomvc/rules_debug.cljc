(ns precept.todomvc.rules-debug
    #?(:cljs (:require-macros [precept.dsl :refer [<- entity]]))
  (:require [clara.rules.accumulators :as acc]
            [clara.rules :as cr]
            [precept.spec.sub :as sub]
            [precept.listeners :as l]
            [precept.schema :as schema]
            [precept.todomvc.schema :refer [app-schema]]
            [precept.util :refer [insert! insert-unconditional! retract! guid] :as util]
            #?(:clj [precept.dsl :refer [<- entity]])
            #?(:clj [precept.tuplerules :refer [def-tuple-session
                                                def-tuple-rule
                                                deflogical
                                                defsub]])
            #?(:cljs [precept.tuplerules :refer-macros [deflogical
                                                        defsub
                                                        def-tuple-session
                                                        def-tuple-rule]])))

(defn trace [& args]
  (apply prn args))

(deflogical [?e :entry/new-title "Hello again!"] :- [[?e :entry/title]])

(def-tuple-rule all-facts
  {:group :report}
  [?facts <- (acc/all) :from [:all]]
  =>
  (println "FACTs at the end!" ?facts))

(def-tuple-rule print-entity
  [[?e :todo/title]]
  [(<- ?entity (entity ?e))]
  =>
  (println "Entity!" ?entity))

(defsub :my-sub
  [?name <- [_ :foo/name]]
  =>
  (let [my-var "x"]
    (println "Heyo" my-var)
    {:foo/name ?name}))

(def-tuple-session app-session
   'precept.todomvc.rules-debug
   :schema app-schema)

(-> app-session
  (l/replace-listener)
  (util/insert [[1 :entry/title "First"]
                [1 :entry/title "Second"]
                [2 :todo/title "First"]
                [3 ::sub/request :my-sub]
                [:transient :test "foo"]
                [2 :todo/title "Second"]])
  (cr/fire-rules)
  (l/vec-ops))

