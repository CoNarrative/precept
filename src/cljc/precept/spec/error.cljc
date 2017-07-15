(ns precept.spec.error
  (:require [clojure.spec.alpha :as s]))


(s/def ::unique-identity-conflict string?)

(s/def ::unique-value-conflict string?)

(s/def ::type #{:unique-conflict})

; TODO. Should be Tuple but would cause circular dependency when this ns
; is used in util
(s/def ::existing-fact any?)
(s/def ::failed-insert any?)
