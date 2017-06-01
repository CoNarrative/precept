(ns precept.impl.rules
    (:require [precept.spec.rulegen :as rulegen]
              [precept.accumulators :as acc]))

;;TODO. Figure out if we can avoid a circular dependency with rules so we can use
;; positional rules syntax

(clara.rules/defrule clean-transients___impl
  {:group :cleanup}
  [?fact <- :all (= :transient (:e this))]
  =>
  (println "Retracting transient!")
  (clara.rules/retract! ?fact))

(clara.rules/defrule entities___impl-a
  {:salience 1}
  [::rulegen/for-macro (= ?req (:e this)) (= :entities (:v this))]
  [:entities/eid (= ?req (:e this)) (= ?e (:v this))]
  [?entity <- (precept.accumulators/all) :from [:all (= ?e (:e this))]]
  =>
  (println "[rulegen] inserting entity!" ?entity)
  (precept.util/insert! [?req :entities/entity ?entity]))

(clara.rules/defrule entities___impl-b
  {:salience 0}
  [:entities/order (= ?req (:e this)) (= ?eids (:v this))]
  [?ents <- (precept.accumulators/all :v) :from [:entities/entity (= ?req (:e this))]]
  =>
  (let [items (group-by :e (flatten ?ents))
        ordered (vals (select-keys items (into [] ?eids)))]
    (println "[rulegen] inserting response for order" ?eids ordered)
    (precept.util/insert! [?req ::rulegen/response ordered])))

(clara.rules/defrule remove-entity___impl
  {:group :action}
  [:remove-entity (= ?v (:v this))]
  [?entity <- (precept.accumulators/all) :from [:all (= ?v (:e this))]]
  =>
  (println "Fulfilling remove entity request " ?entity)
  (doseq [tuple ?entity]
    (precept.util/retract! tuple)))
