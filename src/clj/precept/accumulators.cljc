(ns precept.accumulators
  (:require [clara.rules.accumulators])
  (:refer-clojure :exclude [min max distinct count]))

;; Define Clara's accumulator functions in this ns so users can avoid
;; requiring Clara as a dependency
(def accum clara.rules.accumulators/accum)
(def reduce-to-accum clara.rules.accumulators/reduce-to-accum)
(def grouping-by clara.rules.accumulators/grouping-by)
(def min clara.rules.accumulators/min)
(def max clara.rules.accumulators/max)
(def average clara.rules.accumulators/average)
(def sum clara.rules.accumulators/sum)
(def count clara.rules.accumulators/count)
(def distinct clara.rules.accumulators/distinct)
(def all clara.rules.accumulators/all)

(defn by-fact-id
  "Custom accumulator.

  Like acc/all ewxcept sorts tuples by :t slot (fact-id). Since fact ids are created sequentially
  this sorts facts by order they were created.
  Returns list of facts. Optional `k` arg maps `k` over facts."
  ([]
   (accum
     {:initial-value []
      :reduce-fn (fn [acc cur] (sort-by :t (conj acc cur)))
      :retract-fn (fn [acc cur] (sort-by :t (remove #(= cur %) acc)))}))
  ([k]
   (accum
     {:initial-value []
      :reduce-fn (fn [acc cur] (sort-by :t (conj acc (k cur))))
      :retract-fn (fn [acc cur] (sort-by :t (remove #(= (k cur) %) acc)))})))

(defn list-of
  "Custom accumulator.

  Calls fact-f on facts being accumulated.
  If provided, calls list-f on accumulated list result."
  ([fact-f]
   (accum
     {:initial-value []
      :reduce-fn (fn [acc cur] (fact-f (conj acc cur)))
      :retract-fn (fn [acc cur] (fact-f (remove #(= cur %) acc)))}))
  ([fact-f list-f]
   (accum
     {:initial-value []
      :reduce-fn (fn [acc cur] (list-f (conj acc (fact-f cur))))
      :retract-fn (fn [acc cur] (list-f (remove #(= (fact-f cur) %) acc)))})))
