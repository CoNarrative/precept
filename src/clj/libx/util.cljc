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

(defn insertable
  "Returns vector of tuples"
  [x]
  (cond
    (map? x) (map->tuples x)
    (map? (first x)) (mapcat map->tuples x)
    (vector? (first x)) x
    :else (vector x)))

(defn facts->changes [facts]
  (insertable
    (mapv #(vector (guid) :db/change %)
      (insertable facts))))

(defn with-changes [facts]
  "Takes coll [{:db/id id :a v}...]. Returns then as tuples with
  a :db/change tuple for each"
  (let [xs (insertable facts)]
    (into xs (insertable (mapcat facts->changes xs)))))

(defn insert [session & facts]
  "Inserts either: {} [{}...] [] [[]..]"
  (let [insertables (mapcat insertable facts)]
    (insert-all session insertables)))

(defn retract [session & facts]
  "Retracts either: {} [{}...] [] [[]..]"
  (let [insertables (mapcat insertable facts)]
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

;(defn entity-tuples->entity-map
;  "Takes list of tuples for a *single* entity and returns single map"
;  [tups]
;  (let [e (ffirst tups)
;        _ (println e)]
;    (reduce
;      (fn [acc [_ a v]]
;        (let [one-to-many (one-to-many? a)]
;          (merge acc
;            (if (and (one-to-many a) (contains? acc a))
;              {a (conj (a acc) v)}
;              (merge acc {a v})))))
;      {} tups)))
(defquery qav-
  "(Q)uery (A)ttribute (V)alue.
  Finds facts matching args attribute and value"
  [:?a :?v]
  [:all [[e a v]] (= e ?e) (= a ?a) (= v ?v)])

(defn qav [session a v]
  (query session qav- :?a a :?v v))

(defquery qave-
  "(Q)uery (A)ttribute (V)alue (E)ntity.
  Finds facts matching args attribute, value and eid"
  [:?a :?v :?e]
  [:all [[e a v]] (= e ?e) (= a ?a) (= v ?v)])

(defn qave [session a v e]
  (query session qav- :?a a :?v v :?e e))

(defquery entity-
  [:?e]
  [?entity <- :all [[e a v]] (= ?e e)])

(defn entityv
  [session e]
  (mapv :?entity (query session entity- :?e e)))

(defn entity
  [session e]
  (entity-tuples->entity-map
    (entityv session e)))

(defquery qa-
  [:?a]
  [:all [[e a v]] (= e ?e) (= a ?a) (= v ?v)])

(defn qa [session a]
  (query session qa- :?a a))

(defn keyed-tup->vector-tup [m]
  (into [] (vals m)))

(defquery qe
 [:?e]
 [:all [[e a v]] (= e ?e) (= a ?a) (= v ?v)])

(defn entities-where
  "Returns hydrated entities matching an attribute-only or an attribute-value query"
  ([session a] (map #(entity session (:db/id %)) (clara-tups->maps (qa session a))))
  ([session a v] (map #(entity session (:db/id %)) (clara-tups->maps (qav session a v))))
  ([session a v e] (map #(entity session (:db/id %)) (clara-tups->maps (qave session a v e)))))

(defn facts-where
  "Returns tuples matching a v e query where v, e optional"
  ([session a] (mapv keyed-tup->vector-tup (qa session a)))
  ([session a v] (mapv keyed-tup->vector-tup (qav session a v)))
  ([session a v e] (mapv keyed-tup->vector-tup (qave session a v e))))

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