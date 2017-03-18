(ns todomvc.util
    ;(:require-macros [clara.macros :refer [defrule defquery defsession]])
    (:require [clara.rules :refer [query insert-all fire-rules]]
              [todomvc.macros :as m]))


(defn attr-ns [attr]
  (subs (first (clojure.string/split attr "/")) 1))

(defn- uuid []
  ?# (:cljs (random-uuid)
       :clj (java.util.UUID/randomUUID)))

(defn map->tuple [m]
  (mapv (fn [[a v]] [(:db/id m) a v]) (dissoc m :db/id)))

(defn insert-tuples [session tups]
  (insert-all session (apply concat tups)))

(defn insert-fire!
  "Inserts facts into session and fires rules
    `facts` - Seq containing vec of vecs `'([ [][] ])`"
  [session facts]
  (-> session
    (insert-tuples facts)
    (fire-rules)))

(defn clara-tups->maps
  "Takes seq of ms with keys :?e :?a :?v, joins on :?e and returns
  vec of ms (one m for each entity)"
  [tups]
  (->> (group-by :?e tups)
    (mapv (fn [[id ent]]
            (into {:db/id id}
              (reduce (fn [m tup] (assoc m (:?a tup) (:?v tup)))
                {} ent))))))

(defn entity-tuples->entity-map
  "Takes list of tuples for a *single* entity and returns single map"
  [tups]
  (reduce
    (fn [acc [e a v]]
      (merge acc {:db/id e
                  a      v}))
    {} tups))

(defquery qav-
  "(Q)uery (A)ttribute (V)alue.
  Finds facts matching args attribute and value"
  [:?a :?v]
  [:all [[e a v]] (= e ?e) (= a ?a) (= v ?v)])

(defn qav [session a v]
  (clara-tups->maps
    (query session qav- :?a a :?v v)))

(defquery qave-
  "(Q)uery (A)ttribute (V)alue (E)ntity.
  Finds facts matching args attribute, value and eid"
  [:?a :?v :?e]
  [:all [[e a v]] (= e ?e) (= a ?a) (= v ?v)])

(defn qave [session a v e]
  (clara-tups->maps
    (query session qav- :?a a :?v v :?e e)))

(defquery entity-
  [:?eid]
  [?entity <- :all [[e a v]] (= e ?eid)])

(defn entity
  [session id]
  (entity-tuples->entity-map
    (mapv :?entity (query session entity- :?eid id))))


