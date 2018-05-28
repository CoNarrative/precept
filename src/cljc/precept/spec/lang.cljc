(ns precept.spec.lang
    (:require [clojure.spec.alpha :as s]
              [precept.spec.rulegen :as rulegen]))

(s/def ::variable-binding
  (s/and some? symbol?
    #(clojure.string/starts-with? (name %) "?")))

(s/def ::ops #{'and 'or 'not 'exists :and :or :not :exists})

(s/def ::s-expr list?)

(s/def ::test-expr #{:test})

(s/def ::ignore-slot #{"_" '_})

(s/def ::s-expr-with-binding
  (s/and ::s-expr
    #(some (fn [x] (s/valid? ::variable-binding x)) %)))

(s/def ::value-equals-matcher
  (s/and some?
    #(not (coll? %))
    #(not (s/valid? ::ignore-slot %))
    #(not (s/valid? ::variable-binding %))))

(s/def ::attribute-matcher
  (s/or :tuple-1-keyword (s/tuple keyword?)
        :keyword keyword?))

;; FIXME. Does not appear to pass for a full accumulator condition, though
;; will match the portion that uniquely identifies an accumulator. Create a spec for
;; the full accumulator syntax to avoid confusion
(s/def ::accum-expr
  (s/cat :accum-fn ::s-expr
         :from-symbol #{'from :from}
         :tuple-or-attribute (s/or :attrubute ::attribute-matcher
                                   :tuple ::tuple)))

(s/def ::fact-binding
  (s/cat :variable-binding #(s/valid? ::variable-binding %)
         :arrow-symbol #{'<-}))

(s/def ::special-forms #{'entity 'entities})

(s/def ::special-form
  (s/and seq?
        #(= (first %) '<-)
        #(s/valid? ::variable-binding (second (flatten %)))
        #(s/valid? ::special-forms (nth (flatten %) 2))))

(s/def ::contains-rule-generator
  (s/and ::special-form
         #(s/valid? ::rulegen/generators (nth (flatten %) 2))))

(s/def ::tuple-2
  (s/tuple
    (s/and some? #(not (s/valid? ::s-expr %)))
    any?))
;;FIXME. sexprs are allowed in 3rd slot. A variable binding in :e passes when sexpr in :v,
;; but the production is wrong when a keyword or value is in :e and sexpr in :v
(s/def ::tuple-3
  (s/tuple
    (s/and some? #(not (s/valid? ::s-expr %)))
    any?
    (s/and some? #(not (s/valid? ::s-expr %)))))

(s/def ::tuple-4
  (s/tuple
    (s/and some? #(not (s/valid? ::s-expr %)))
    any?
    (s/and any? #(not (s/valid? ::s-expr %)))
    (s/and some?
      (s/or :value-match-t number?
            :bind-to-t ::variable-binding))))

(s/def ::tuple
  (s/or :tuple-2 ::tuple-2
        :tuple-3 ::tuple-3
        :tuple-4 ::tuple-4))

;(s/def ::session #(= % (type clara.rules.engine/ISession)))

