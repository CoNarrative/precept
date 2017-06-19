(ns precept.spec.fact
    (:require [clojure.spec :as s]))


(s/def ::entity-map
  (s/keys :req [:db/id]))

(s/def ::tuple
  (s/tuple
    some?
    keyword?
    some?))
