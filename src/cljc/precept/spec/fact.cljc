(ns precept.spec.fact
    (:require [clojure.spec.alpha :as s]))


(s/def ::entity-map
  (s/keys :req [:db/id]))

(s/def ::tuple
  (s/tuple
    any?
    keyword?
    any?))
