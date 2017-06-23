(ns precept.spec.core
  (:require [clojure.spec :as s]))

(defn validate [spec value]
  (let [msg (s/explain-str spec value)]
    (condp = msg
      "Success!\n" true
      (ex-info msg {}))))

(defn conform-or-nil
  "Takes a spec and x, a collection or value to test. If x is coll, returns first item in
  coll that conforms to spec or nil. If x is not a collection returns x if it conforms to spec
  or nil."
  [spec x]
  (some
    (comp #(when-not (= :clojure.spec/invalid %) %)
          #(s/conform spec %))
    (if (coll? x) x (vector x))))
