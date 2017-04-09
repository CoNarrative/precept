(ns libx.core
    (:require [libx.util :refer [entity-tuples->entity-map]]
      #?(:clj [clojure.core.async :refer [<! >! put! chan go-loop]])
      #?(:clj [reagent.ratom :as rr])
      #?(:cljs [cljs.core.async :refer [put! chan <! >!]])
      #?(:cljs [reagent.core :as r]))
  #?(:cljs (:require-macros [cljs.core.async.macros :refer [go go-loop]])))
    ;#?(:clj [reagent.dom.server :refer [render-to-string]])

(defn mk-ratom [args]
  #?(:clj (atom args) :cljs (r/atom args)))

(def state (mk-ratom {[:foo] "Hey"}))

(def registry (atom nil))

(def changes-ch (chan 1))

(defn lens [a path]
  #?(:clj #(get-in state path) :cljs (r/cursor a path)))

(defn register [path]
  (if-let [existing (get registry path)]
    existing
    (let [lens (lens state path)]
      (swap! registry assoc path lens)
      lens)))

(defn subscribe [paths]
  "Returns atom of r/cursors, one for each path in paths in cljs
  Idea is that we combine multiple lenses and add access to each by the last key
  of any 'path' vec supplied to this function, while maintaining reactivity for each.
  Presumes a transitive property of dereferencing that may or may not pan out."
  (let [existing (select-keys @state paths)
        new (remove (set (keys existing)) paths)]
    (mk-ratom (merge existing
                (reduce (fn [acc path] (assoc acc (last path) @(register path)))
                  {} new)))))

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
  (swap! atom merge (-> changes
                      (dissoc :op)
                      (dissoc :db/id)))) ; temp
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
            _ (println "Change id op atom" change id op)]
        (condp = op
          :add (do (add state change) (recur))
          :remove (do (del state change) (recur))
          :default (println "No match for" change)))
      (recur


;; test
;(def changes (embed-op {:added [[-1 :done-count 1000]
                                [-1 :active-count 5]))))
;(for [change changes]
;  (put! changes-ch change))

;@state
;@registry
