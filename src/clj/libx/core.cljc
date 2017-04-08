(ns libx.core
  (:require [libx.util :refer [entity-tuples->entity-map]]
    #?(:clj [clojure.core.async :refer [<! >! put! chan go-loop]])
    #?(:cljs [cljs.core.async :refer [put! chan <! >!]])
    #?(:cljs [reagent.core :as r]))
  #?(:cljs (:require-macros [cljs.core.async.macros :refer [go go-loop]])))
    ;#?(:clj [reagent.dom.server :refer [render-to-string]])

(def registry (atom nil))

(def changes-ch (chan 1))

(defn mk-atom []
  #?(:clj (atom {}) :cljs (r/atom {})))

(defn register [id]
  (let [atom (mk-atom)]
    (swap! registry assoc id atom)
    atom))

(defn subscribe [id]
  "Returns ratom in cljs or atom in clj"
  (register id))

(defn with-op [change op-kw]
  (mapv (fn [ent] (conj ent (vector (ffirst ent) :op op-kw)))
    (partition-by first change)))

(defn embed-op [changes]
  (let [added (:added changes)
        removed (:removed changes)]
    (mapv entity-tuples->entity-map
      (into (with-op added :add)
        (with-op removed :remove)))))

(defn clean-changes [changes]
  "Removes :op, :db/id from change for an entity"
  (remove #(#{:op :db/id} (first %)) changes))

(defn add [atom changes]
  "Merges changes into atom"
  (swap! atom merge (dissoc changes :op)))

(defn del [atom changes]
  "Removes keys in change from atom"
  (swap! atom (fn [m] (apply dissoc m (keys (clean-changes changes))))))

(defn router [in-ch registry]
  "Reads changes from channel and updates atoms in registry
   * `in-ch` - core.async channel
   * `registry` - atom"
  (go-loop []
    (if-let [change (<! in-ch)]
      (let [id (:db/id change)
            op (:op change)
            atom (get @registry id)]
        (if (= :add op)
          (do (add atom change) (recur))
          (do (del atom change) (recur))))
      (recur))))


;; test
;(def changes {:added [[123 :attr/a 42]
;                      [123 :attr/b "x"]
;                      [456 :attr/a "foo"]
;                      [789 :attr/b "baz"]]
;              :removed [[123 :attr/a 42]
;                        [456 :attr/a "foo"]]})
;
;(def ^:dynamic *foo* (router changes-ch registry))
;@registry
;(subscribe 123)
;(subscribe 456)
;(subscribe 789)
;(for [change (embed-op changes)]
;  (put! changes-ch change))
