(ns precept.spec.event
  (:require [clojure.spec.alpha :as s]
            [precept.spec.lang :as lang]
            [precept.util]
   #?(:cljs [precept.util :refer [Tuple]]))
  #?(:clj (:import [precept.util Tuple])))

(s/def ::type #{:add-facts :add-facts-logical :retract-facts :retract-facts-logical})

(s/def ::action boolean?)

(s/def ::state-number integer?)

(s/def ::event-number integer?)

(s/def ::facts (s/every (s/or :Tuple-record #(= Tuple (type %))
                              :fact-map (s/every (comp #{:e :a :v :t} first)))))

(s/def ::matches (s/and vector? (s/every vector?)))

(s/def ::bindings (s/map-of
                    (s/and keyword? #(s/valid? ::lang/variable-binding (symbol (name %))))
                    any?))

(s/def ::name (s/and string? #(clojure.string/includes? % "/")))

(s/def ::display-name string?)

(s/def ::ns-name symbol?)

(s/def ::rhs list?)

(s/def ::props (s/or :provided map? :omitted nil?))


(s/def ::op-prefixed-condition #(s/valid? ::lang/ops (first %)))

(s/def ::condition-map (s/every-kv #{:type :constraints}
                                   (s/or :kw keyword? :vec vector?)))

(s/def ::accumulator-map (s/every-kv #{:accumulator :from :result-binding}
                                    any?))

(s/def ::condition (s/or :op-prefixed-condition ::op-prefixed-condition
                         :condition-map ::condition-map
                         :accumulator-map ::accumulator-map))

(s/def ::lhs (s/every ::condition))

;(s/valid? ::lhs
;  [[:not {:type :active-discount, :constraints []}]
;   {:type :summed-subtotals, :constraints [(= '?total (:v 'this))]}])

(s/def ::event (s/keys :req-un [::state-number ::event-number ::type ::facts]
                       :opt-un [::name ::display-name ::ns-name ::lhs ::rhs
                                ::props ::bindings ::matches]))
;(let [event-1 {:bindings {:?sum 0},
;               :name "fullstack.rules/define-563499098",
;               :type :add-facts-logical,
;               :ns-name 'fullstack.rules,
;               :lhs
;                         [{:accumulator '(precept.accumulators/sum :v),
;                           :from {:type :cart-item/subtotal, :constraints []},
;                           :result-binding :?sum}
;                          [:not {:type :active-discount, :constraints []}]
;                          {:type :summed-subtotals, :constraints [(= '?total (:v 'this))]}]
;               :event-number 2,
;               :matches [[0 100]],
;               :display-name
;               "[{:accumulator (precept.accumulators/sum :v), :from {:type :cart-item/subtotal, :constraints []}, :result-binding :?sum}]",
;               :facts '({:e :app, :a :summed-subtotals, :v 0, :t 2}),
;               :rhs '(do (precept.util/insert! [:app :summed-subtotals '?sum])),
;               :state-number 0,
;               :props nil}]
;  (s/explain ::event event-1))

;(s/explain ::event {:state-number 1 :event-number 2 :type :add-facts
;                    :facts '({:e :foo :a :foo :v []})})
;
;(let [x
;      {:bindings {:?sum 44.73},
;       :name "fullstack.rules/define-563499098",
;       :type :add-facts-logical,
;       :ns-name 'fullstack.rules,
;       :lhs [{:from {:constraints [], :type :cart-item/subtotal},
;              :result-binding :?sum,
;              :accumulator '(precept.accumulators/sum :v),}]
;       :event-number 12,
;       :matches [[44.73 100]],
;       :id #uuid "b7d24fef-2850-4c0c-8eb4-f6b4d4a732c6",
;       :display-name "[{:accumulator (precept.accumulators/sum :v), :from {:type :cart-item/subtotal, :constraints []}, :result-binding :?sum}]",
;       :state-id #uuid "6c455f91-d8a9-40b3-a330-6047dc6b6365",
;       :facts '({:v 44.73, :e :app, :t 551, :a :summed-subtotals}),
;       :rhs '(do (precept.util/insert! [:app :summed-subtotals ?sum])),
;       :state-number 8,
;       :props nil}]
;  (some-> x :lhs first :from))