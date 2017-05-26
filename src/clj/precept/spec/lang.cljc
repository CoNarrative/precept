(ns precept.spec.lang
    (:require [clojure.spec :as s]
              [precept.spec.rulegen :as rulegen]))

(s/def ::variable-binding
  (s/and some? symbol?
    #(clojure.string/starts-with? (name %) "?")))

(s/def ::s-expr list?)

(s/def ::test-expr #{:test})

(s/def ::ignore-slot #{"_" '_})

(s/def ::value-equals-matcher
  (s/and some?
    #(not (coll? %))
    #(not (s/valid? ::ignore-slot %))
    #(not (s/valid? ::variable-binding %))))

(s/def ::attribute-matcher
  (s/or :tuple-1-keyword (s/tuple keyword?)
        :keyword keyword?))

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
    keyword?))

(s/def ::tuple-3
  (s/tuple
    (s/and some? #(not (s/valid? ::s-expr %)))
    keyword?
    (s/and some? #(not (s/valid? ::s-expr %)))))

(s/def ::tuple-4
  (s/tuple
    (s/and some? #(not (s/valid? ::s-expr %)))
    keyword?
    (s/and some? #(not (s/valid? ::s-expr %)))
    (s/and some?
      (s/or :match-tx-id number?
            :bind-to-tx-id ::variable-binding))))

(s/def ::tuple
  (s/or :tuple-2 ::tuple-2
        :tuple-3 ::tuple-3
        :tuple-4 ::tuple-4))
