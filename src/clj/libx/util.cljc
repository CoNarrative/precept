(ns libx.util
    #?(:cljs
       (:require [clara.rules
                  :refer [query insert-all fire-rules]
                  :refer-macros [defquery]]))
    #?(:clj
       (:require [clara.rules :refer [query defquery insert-all fire-rules]])))


(defn attr-ns [attr]
  (subs (first (clojure.string/split attr "/")) 1))

(defn map->tuples
  "Transforms entity map to vector of tuples
  {a1 v1 a2 v2 :db/id eid} -> [ [eid a1 v1] ... ]"
  [m]
  (mapv (fn [[a v]] [(:db/id m) a v]) (dissoc m :db/id)))

(defn insertable [x]
  (cond
    (map? x) (map->tuples x)
    (map? (first x)) (mapcat map->tuples x)
    (vector? (first x)) x
    :else (vector x)))

(defn insert [session & facts]
  "Inserts either: {} [{}...] [] [[]..]"
  (println "Facts rec'd!" facts)
  (let [insertables (mapcat insertable facts)]
    (println "Insertables!" insertables)
    (insert-all session insertables)))

(defn retract [session & facts]
  "Retracts either: {} [{}...] [] [[]..]"
  (println "facts rec'd" facts)
  (let [insertables (mapcat insertable facts)]
    (println "Retractables?!" insertables)
    (println "RETRACTING!" insertables)
    (apply (partial clara.rules/retract session) insertables)))

(defn replace! [session this that]
  (-> session
    (retract this)
    (insert that)))

; Not a true modify...going fast will come back. Thinking fn argument like updateIn
;(defn modify [session old new]
;  (-> session
;    (retract old)
;    (insert new)))

(defn insert-fire!
  "Inserts facts into session and fires rules
    `facts` - vec of vecs `[ [] ... ]`"
  [session facts]
  (-> session
    (insert facts)
    (fire-rules)))

(defn retract-fire!
  "Inserts facts into session and fires rules
    `facts` - vec of vecs `[ [] ... ]`"
  [session facts]
  (-> session
    (retract facts)
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
  (clara-tups->maps
    (query session qa- :?a a)))

(defn keyed-tup->vector-tup [m]
  (into [] (vals m)))

(defquery qe
 [:?e]
 [:all [[e a v]] (= e ?e) (= a ?a) (= v ?v)])

(defn entities-where
  "Returns hydrated entities matching an attribute-only or an attribute-value query"
  ([session a] (map #(entity session (:db/id %)) (qa session a)))
  ([session a v] (map #(entity session (:db/id %)) (qav session a v)))
  ([session a v e] (map #(entity session (:db/id %)) (qave session a v e))))

(defn facts-where
  "Returns tuples matching a v e query where v, e optional"
  ([session a] (mapv keyed-tup->vector-tup (qa session a)))
  ([session a v] (mapv keyed-tup->vector-tup (qav session a v)))
  ([session a v e] (mapv keyed-tup->vector-tup (qave session a v e))))
