(ns precept.spec.rulegen
  (:require [clojure.spec.alpha :as s]
            [precept.spec.test :as test]))

(s/def ::generators #{'entities})

(s/def ::request
  (s/and
    keyword?
    (s/or
      :generator-ns
      #(s/valid? ::generators (symbol (or (namespace %) "default")))
      :generator-keyword
      #(s/valid? ::generators (symbol (name %))))))

(s/def ::response any?)

(s/def ::for-macro keyword?)

(s/def ::request-params any?)

;; entities macro
(s/def :precept.spec.rulegen.entities/eid string?)
(s/def :precept.spec.rulegen.entities/order any?)
(s/def :precept.spec.rulegen.entities/entity any?)
