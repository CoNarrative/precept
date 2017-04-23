(ns libx.util
    #?(:cljs
       (:require [clara.rules :as cr
                  :refer [query insert-all fire-rules]
                  :refer-macros [defquery]]))
    #?(:clj
       (:require [clara.rules :as cr :refer [query defquery insert-all fire-rules]])))

(defn guid []
  #?(:clj (java.util.UUID/randomUUID)
     :cljs (random-uuid)))

(defn attr-ns [attr]
  (subs (first (clojure.string/split attr "/")) 1))

(defn map->tuples
  "Transforms entity map to vector of tuples
  {a1 v1 a2 v2 :db/id eid} -> [ [eid a1 v1] ... ]"
  [m]
  (mapv (fn [[a v]] [(:db/id m) a v]) (dissoc m :db/id)))

(defn keyed-tup->vector-tup [m]
  (into [] (vals m)))

(defrecord Tuple [e a v])

(defn third [xs]
  #?(:cljs (nth xs 2)
     :clj (try (nth xs 2)
           (catch java.lang.IndexOutOfBoundsException e
             (throw (ex-info "Received tuple without third slot" {}))))))

(defn record->vec [r]
  (let [v-pos (:v r)
        v (if (and (record? v-pos)
                (not (record? (first v-pos))))
            (record->vec v-pos)
            v-pos)]
    (vector (:e r) (:a r) v)))

(defn vec->record [vec]
  (let [v-pos (third vec)
        v (if (and (vector? v-pos)
                   (not (record? (first v-pos))))
            (vec->record v-pos)
            v-pos)]
    (->Tuple (first vec)
             (second vec)
             v)))

(defn tuplize
  "Returns [[]...] no matter what.
  Arg may be {} [{}...] [] [[]...]"
  [x]
  (cond
    (map? x) (map->tuples x)
    (map? (first x)) (mapcat map->tuples x)
    (vector? (first x)) x
    :else (vector x)))

(defn facts->changes [facts]
  (tuplize
    (mapv #(vector (guid) :db/change %)
      (tuplize facts))))

(defn with-changes [facts]
  "Takes coll [{:db/id id :a v}...]. Returns then as tuples with
  a :db/change tuple for each"
  (let [xs (tuplize facts)]
    (into xs (tuplize (mapcat facts->changes xs)))))

(defn insertable-facts [facts]
  "Arguments can be any mixture of vectors and records
  Ensures [], [[]...] conform to Tuple record instances."
  (if (vector? facts)
    (if (vector? (first facts))
      (map vec->record facts)
      (vector (vec->record facts)))
    (vector facts)))

(defn insert [session & facts]
  "Inserts Tuples. Accepts {} [{}...] [] [[]...]"
  (let [insertables (map vec->record (mapcat tuplize facts))]
    (insert-all session insertables)))

(defn insert! [facts]
  (let [insertables (map vec->record (mapcat tuplize (list facts)))]
    (cr/insert-all! insertables)))

(defn insert-unconditional! [facts]
  (let [insertables (map vec->record (mapcat tuplize (list facts)))]
    (cr/insert-all-unconditional! insertables)))

(defn retract! [facts]
  "Wrapper around Clara's `retract!`. To be used within RHS of rule only. "
  (let [insertables (insertable-facts facts)]
    (doseq [to-retract insertables]
      (cr/retract! to-retract))))

(defn retract [session & facts]
  "Retracts either: Tuple, {} [{}...] [] [[]..]"
  (let [insertables (map vec->record (mapcat tuplize facts))]
    (apply (partial cr/retract session) insertables)))

(defn replace! [session this that]
  (-> session
    (retract this)
    (insert that)))

(defn insert-fire
  "Inserts facts into session and fires rules
    `facts` - vec of vecs `[ [] ... ]`"
  [session facts]
  (-> session
    (insert facts)
    (fire-rules)))

(defn retract-fire
  "Inserts facts into session and fires rules
    `facts` - vec of vecs `[ [] ... ]`"
  [session facts]
  (-> session
    (retract facts)
    (fire-rules)))

;TODO. Does not support one-to-many. Attributes will collide
(defn clara-tups->maps
  "Takes seq of ms with keys :?e :?a :?v, joins on :?e and returns
  vec of ms (one m for each entity)"
  [tups]
  (->> (group-by :?e tups)
    (mapv (fn [[id ent]]
            (into {:db/id id}
              (reduce (fn [m tup] (assoc m (:?a tup) (:?v tup)))
                {} ent))))))

(defn clara-tups->tups
  [tups]
  (mapv (fn [m] [(:?e m) (:?a m) (:?v m)]) tups))

;TODO. Does not support one-to-many. Attributes will collide
(defn entity-tuples->entity-map
  "Takes list of tuples for a *single* entity and returns single map"
  [tups]
  (reduce
    (fn [acc [e a v]]
      (merge acc {:db/id e
                  a      v}))
    {} tups))

(defn tuples->maps [tups]
  "Returns vec of hydrated ms from tups"
  (mapv #(entity-tuples->entity-map (second %)) (group-by first tups)))


;; From clojure.core.incubator
(defn dissoc-in
  "Dissociates an entry from a nested associative structure returning a new
  nested structure. keys is a sequence of keys. Any empty maps that result
  will not be present in the new structure."
  [m [k & ks :as keys]]
  (if ks
    (if-let [nextmap (get m k)]
      (let [newmap (dissoc-in nextmap ks)]
        (if (seq newmap)
          (assoc m k newmap)
          (dissoc m k)))
      m)
    (dissoc m k)))