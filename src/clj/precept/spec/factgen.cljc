(ns precept.spec.factgen
  (:require [clojure.spec :as s]))


(s/def ::generators #{'entities})

(s/def ::request (keyword #(s/conform ::generators %)))

(s/def ::response any?)




