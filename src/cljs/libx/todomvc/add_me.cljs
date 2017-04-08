(ns libx.todomvc.add-me
  (:require [libx.listeners :refer [add-listener remove-fact-listeners] :as l]
            [cljs.core.async :refer [put!]]))

(defn replace-listener [session]
  (-> session
    (remove-fact-listeners)
    (add-listener)))

(defn ops [listeners]
  (l/split-ops (first (l/fact-events listeners))))

(defn send! [ch changes]
  (doseq [change changes]
    (put! ch change)))