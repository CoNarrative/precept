(ns libx.spec.core
  (:require [clojure.spec :as s]))

(defn validate [spec value]
  (let [msg (s/explain-str spec value)]
    (condp = msg
      "Success!\n" true
      (ex-info msg {}))))


