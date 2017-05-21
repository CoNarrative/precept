(ns precept.spec.factgen
  (:require [clojure.spec :as s]
            [precept.spec.lang :as lang]))


(s/def ::generators #{'entities})

(s/def ::request (keyword #(s/conform ::generators %)))

(s/def ::response any?)




