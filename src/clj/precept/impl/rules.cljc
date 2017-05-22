(ns precept.impl.rules
  (:require [clara.rules :as cr]
            [clara.rules.accumulators :as acc]
            [precept.util :as util]
            [precept.spec.factgen :as factgen]))

;;TODO. Figure out if we can avoid a circular dependency with tuplerules so we can use
;; positional rules syntax

(cr/defrule clean-transients___impl
  {:group :cleanup}
  [?fact <- :all (= :transient (:e this))]
  =>
  (println "Retracting transient!")
  (cr/retract! ?fact))

(cr/defrule entities___impl-a
  [::factgen/request (= ?req (:e this) (= :entities (:a this)))]
  [:entities/eid (= ?req (:e this) (= ?e (:v this)))]
  [?entity <- (acc/all) :from [:all (= ?e (:e this))]]
  =>
 (util/insert! [?req :entities/entity ?entity]))

(cr/defrule entities___impl-b
  [:entities/order (= ?req (:e this) (= ?eids (:v this)))]
  [?ents <- (acc/all :v) :from [:entities/entity (= ?req (:e this))]]
  =>
  (let [items (group-by :e (flatten ?ents))
        ordered (vals (select-keys items (into [] ?eids)))]
    (util/insert! [?req ::factgen/response ordered])))

