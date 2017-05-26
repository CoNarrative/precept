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
;              [precept.todomvc.schema :refer [app-schema]]
;              [precept.util :refer [insert! insert-unconditional! retract! guid] :as util]
;      #?(:clj
;              [precept.dsl :refer [<- entity entities]])
;      #?(:clj
;              [precept.tuplerules :refer [def-tuple-session
;                                          def-tuple-rule
;                                          deflogical
;                                          defsub]])
;      #?(:cljs [precept.tuplerules :refer-macros [deflogical
;                                                  defsub
;                                                  def-tuple-session
;                                                  def-tuple-rule]])))
;
;(defn trace [& args]
;  (apply prn args))
;
;(deflogical [?e :entry/new-title "Hello again!"] :- [[?e :entry/title]])
;
;(def-tuple-rule all-facts
;  {:group :report}
;  [?facts <- (acc/all) :from [:all]]
;  =>
;  (do nil))
;  ;(println "FACTs at the end!" ?facts))
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
;(def-tuple-rule log-errors
;  [[?e ::err/type]]
;  [(<- ?error (entity ?e))]
;  =>
;  (println "Found error!" ?error))
;
;
;(def-tuple-session app-session
;   'precept.todomvc.rules-debug
;   :schema app-schema)
;;(reset! precept.state/fact-index {})
;
;(-> app-session
;  (l/replace-listener)
;  (util/insert [[1 :entry/title "First"]
;                [1 :entry/title "Second"]
;                [2 :todo/title "First"]
;                [3 ::sub/request :my-sub]
;                [:transient :test "foo"]
;                [1 :interesting-fact 42]
;                [2 :interesting-fact 42]
;                [3 :interesting-fact 42]
;                [4 :interesting-fact 42]
;                [2 :todo/title "Second"]
;                [3 :todo/title "Second"]
;                [5 ::sub/request :my-sub-2]])
;  (cr/fire-rules)
;  (l/vec-ops))
;
;(l/vec-ops app-session)
;
