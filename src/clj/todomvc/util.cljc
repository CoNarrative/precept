(ns todomvc.util
    #?(:cljs
       (:require [clara.rules
                  :refer [query insert-all fire-rules]
                  :refer-macros [defquery]]))
    #?(:clj
       (:require [clara.rules :refer [query defquery insert-all fire-rules]])))


(defn attr-ns [attr]
  (subs (first (clojure.string/split attr "/")) 1))

(defn map->tuple [m]
  (mapv (fn [[a v]] [(:db/id m) a v]) (dissoc m :db/id)))

(defn insert-tuples [session tups]
  (insert-all session (apply concat tups))) ;; into seq? might solve one vs many and be
  ;; performant (no-op)

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

(defquery find-by-attribute-
  [:?a]
  [:all [[e a v]] (= e ?e) (= a ?a) (= v ?v)])

(defn find-by-attribute [session kw]
  (clara-tups->maps
    (query session find-by-attribute- :?a kw)))

(defn keyed-tup->vector-tup [m]
  (into [] (vals m)))


(defn entities-where
  "Returns hydrated entities matching an attribute-only or an attribute-value query"
  ([session a] (map #(entity session (:db/id %)) (find-by-attribute session a)))
  ([session a v] (map #(entity session (:db/id %)) (qav session a v)))
  ([session a v e] (map #(entity session (:db/id %)) (qave session a v e))))

(defn facts-where
  "Returns tuples matching a v e query where v, e optional"
  ([session a] (mapv keyed-tup->vector-tup (find-by-attribute session a)))
  ([session a v] (mapv keyed-tup->vector-tup (qav session a v)))
  ([session a v e] (mapv keyed-tup->vector-tup (qave session a v e))))
