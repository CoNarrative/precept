(ns precept.spec.sub
  (:require [clojure.spec.alpha :as s]))

(s/def ::name keyword?)

(s/def ::request
  (s/or :name-only (s/tuple ::name)
        :name-and-params (s/tuple ::name ::params)))

(s/def ::params #(not (coll? %)))

(s/def ::response any?)

(s/def ::resonponse-id uuid?)
