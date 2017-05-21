(ns precept.todomvc.rules-debug
    #?(:cljs (:require-macros [precept.dsl :refer [<- entity]]))
  (:require [clara.rules.accumulators :as acc]
            [clara.rules :as cr]
            [precept.spec.sub :as sub]
            [precept.spec.factgen :as factgen]
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


;; User writes

(def-tuple-rule my-rule
  [?eids <- (acc/all :e) :from [:interesting-fact]] ;; Maybe new special form `(eids [:interesting-fact])`
  [(<- ?interesting-entities (entities ?eids))]
  =>
  ;; Prints list of Tuples
  (println "Found entities with interesting fact" ?interesting-entities))
  ;(println "Found entities with interesting fact" ?eids))

;; We generate:

;; A rule that contains the accumulator expression with the binding provided in `(entities)`
(def-tuple-rule my-rule___split-0
  [?eids <- (acc/all :e) :from [:interesting-fact]]
  =>
  (let [req-id (guid)
        gen-fact-req [req-id ::factgen/request :entities]]
    ;_ (swap! state/generated-facts assoc req-id {:req gen-fact-req :rule-name ???})
    (insert! gen-fact-req)
    (insert! [req-id :entities/order ?eids])
    (doseq [eid ?eids]
      (insert! [req-id :entities/eid eid]))))

;: ...and the original, rewritten to match on the ::gen-fact/response, keeping the same name
(def-tuple-rule my-rule
  ;; ...rest LHS
  ;; Want to remove acc expr (it exists in a genned rule),
  ;; but then no access to its bound variable inside this rule...
  [?eids <- (acc/all :e) :from [:interesting-fact]]
  ;; Replaces `entities` on same "line" as original
  [[_ ::factgen/response ?interesting-entities]]
  =>
  (println "Found genned response" ?interesting-entities))
   ;; ...original RHS

;; precept.rules.impl:

;; These are "always on", waiting for an :entity request

(def-tuple-rule entities___impl-a
  [[?req ::factgen/request :entities]]
  [[?req :entities/eid ?e]]
  [(<- ?entity (entity ?e))]
  =>
  (insert! [?req :entities/entity ?entity]))

(def-tuple-rule entities___impl-b
  [[?req :entities/order ?eids]]
  [?ents <- (acc/all :v) :from [?req :entities/entity]]
  =>
  (let [items (group-by :e (flatten ?ents))
        ordered (vals (select-keys items (into [] ?eids)))]
     (insert! [?req ::factgen/response ordered])))

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
                [1 :interesting-fact 42]
                [2 :interesting-fact 42]
                [3 :interesting-fact 42]
                [4 :interesting-fact 42]
                [2 :todo/title "Second"]])
  (cr/fire-rules)
  (l/vec-ops))

