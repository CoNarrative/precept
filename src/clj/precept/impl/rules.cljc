(ns precept.impl.rules
  (:require [precept.spec.factgen :as factgen]))

;;TODO. Figure out if we can avoid a circular dependency with tuplerules so we can use
;; positional rules syntax

(clara.rules/defrule clean-transients___impl
  {:group :cleanup}
  [?fact <- :all (= :transient (:e this))]
  =>
  (println "Retracting transient!")
  (clara.rules/retract! ?fact))

(clara.rules/defrule entities___impl-a
  {:salience 1}
  [::factgen/for-macro (= ?req (:e this)) (= :entities (:v this))]
  [:entities/eid (= ?req (:e this)) (= ?e (:v this))]
  [?entity <- (clara.rules.accumulators/all) :from [:all (= ?e (:e this))]]
  =>
  (println "[rulegen] inserting entity!" ?entity)
  (precept.util/insert! [?req :entities/entity ?entity]))

(clara.rules/defrule entities___impl-b
  {:salience 0}
  [:entities/order (= ?req (:e this)) (= ?eids (:v this))]
  [?ents <- (clara.rules.accumulators/all :v) :from [:entities/entity (= ?req (:e this))]]
  =>
  (let [items (group-by :e (flatten ?ents))
        ordered (vals (select-keys items (into [] ?eids)))]
    (println "[rulegen] inserting response for order" ?eids ?ents)
    (precept.util/insert! [?req ::factgen/response ordered])))

