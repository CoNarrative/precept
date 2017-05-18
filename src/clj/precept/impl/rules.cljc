(ns precept.impl.rules
  (:require [clara.rules :as cr]))


(cr/defrule clean-transients___impl
  {:group :cleanup}
  [?fact <- :all (= :transient (:e this))]
  =>
  (println "Retracting transient!")
  (cr/retract! ?fact))
