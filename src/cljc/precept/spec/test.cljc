(ns precept.spec.test
  (:require [clojure.spec.alpha :as s]))

(s/def ::unique-identity any?)

(s/def ::one-to-many any?)

(s/def ::one-to-one any?)

(s/def ::unique-value any?)



