(ns libx.util
   (:require [clara.rules :as cr]
             [libx.state :as state]))

(defn trace [& args]
  (comment (apply prn args)))

(defn guid []
  #?(:clj (java.util.UUID/randomUUID)
     :cljs (random-uuid)))

(defn attr-ns [attr]
  (subs (first (clojure.string/split attr "/")) 1))

(defn map->tuples
  "Transforms entity map to vector of tuples
  {a1 v1 a2 v2 :db/id eid} -> [[eid a1 v1]...]"
  [m]
  (mapv (fn [[k v]] (vector (:db/id m) k v))
    (dissoc m :db/id)))

(defn entity-Tuples->entity-maps
  [coll]
  (mapv
    #(reduce (fn [acc m] (assoc acc :db/id (:e m) (:a m) (:v m)))
       {}
       %)
    coll))

(defn next-fact-id! [] (swap! state/fact-id inc))
(defn reset-fact-id! [] (reset! state/fact-id -1))

(defrecord Tuple [e a v t])

(defn Tuple-3 [e a v] (map->Tuple {:e e :a a :v v}))

(defn add-fact-id [tuple-3]
  (assoc tuple-3 :t (next-fact-id!)))

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
            v-pos)
        tx-id (nth vec 3 (next-fact-id!))]
    (->Tuple (first vec)
             (second vec)
             v
             tx-id)))

(defn tuple-vec->action-hash-map
  "Puts ks in x into e-a-v map and assigns m to :v"
  [x]
  (let [m (nth x 2)]
    (into m {:e (first x) :a (second x) :v m :t (next-fact-id!)})))

(defn gen-Tuples-from-map [m]
  (reduce
    (fn [acc [k v]] (conj acc (->Tuple (guid) k v (next-fact-id!))))
    []
    m))

(defn action-insert! [m]
  (cr/insert-all-unconditional! (gen-Tuples-from-map m)))

(defn tuplize-into-vec
  "Returns [[]...].
  Arg may be {} [{}...] [] [[]...]"
  [x]
  (cond
    (map? x) (map->tuples x)
    (map? (first x)) (mapcat map->tuples x)
    (vector? (first x)) x
    :else (vector x)))

(defn insertable
  "Arguments can be any mixture of vectors and records
  Ensures [], [[]...], Tuple, '(Tuple ...) conform to Tuple record instances."
  [x]
  (cond
    (record? x) (vector x)
    (and (coll? x) (record? (first x))) (into [] x)
    (and (coll? x) (vector? (first x))) (mapv vec->record x)
    (vector? x) (vector (vec->record x))))

(defn insert
  "Inserts Tuples from outside rule context.
  Accepts {} [{}...] [] [[]...]"
  [session facts]
  (let [insertables (insertable facts)
        _ (trace "insert received : " facts)
        _ (trace "insert : " insertables)]
    (cr/insert-all session insertables)))

(defn insert-action
  "Inserts hash-map from outside rule context.
  Accepts [e a v] where v is {} with ks that become part of inserted map"
  [session action]
  (trace "insert-action : " (tuple-vec->action-hash-map action))
  (cr/insert session (tuple-vec->action-hash-map action)))

(defn insert!
  "Inserts Facts within rule context"
  [facts]
  (let [insertables (insertable facts)]
    (trace "insert! : " insertables)
    (cr/insert-all! insertables)))

(defn insert-unconditional!
  "Inserts uncondtinally Facts within rule context"
  [facts]
  (let [insertables (insertable facts)]
    (trace "insert-unconditional! received" facts)
    (trace "insert-unconditional! : " insertables)
    (cr/insert-all-unconditional! insertables)))

(defn retract!
  "Wrapper around Clara's `retract!`.
  To be used within RHS of rule only. Converts all input to Facts"
  [facts]
  (let [insertables (insertable facts)
        _ (trace "retract! :" insertables)]
    (doseq [x insertables]
      (cr/retract! x))))

(defn retract
  "Retracts either: Tuple, {} [{}...] [] [[]..]"
  [session facts]
  (let [insertables (insertable facts)]
    (trace "retract : " insertables)
    (apply (partial cr/retract session) insertables)))

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

;TODO. Does not support one-to-many. Attributes will collide
(defn tuple-entity->hash-map-entity
  "Takes list of tuples for a *single* entity and returns single map"
  [tuples]
  (reduce
    (fn [acc [e a v]]
      (merge acc {:db/id e
                  a v}))
    {} tuples))

(defn get-index-of
  [coll x not-found-idx]
  (let [idx (.indexOf coll x)]
    (if (get coll idx) idx not-found-idx)))

(defn make-activation-group-fn
  "Reads from optional third argument to rule.
  `super` boolean
  `group` keyword
  `salience` number
  Rules marked super will be present in every agenda phase."
  [default-group]
  (fn [m] {:salience (or (:salience (:props m)) 0)
           :group (or (:group (:props m)) default-group)
           :super (:super (:props m))}))

(defn make-activation-group-sort-fn
  [groups default-group]
  (let [default-idx (.indexOf groups default-group)]
    (fn [a b]
      (let [group-a (get-index-of groups (:group a) default-idx)
            group-b (get-index-of groups (:group b) default-idx)
            a-super? (:super a)
            b-super? (:super b)]
        (cond
          a-super? true
          b-super? false
          (and a-super? b-super?) (> (:salience a) (:salience b))
          (< group-a group-b) true
          (= group-a group-b) (> (:salience a) (:salience b))
          :else false)))))

(defn action? [a] (> (.indexOf (name a) "-action") -1))

(defn make-ancestors-fn
  ([hierarchy]
   #(or ((:ancestors hierarchy) %)
      (cond
        (action? %) #{:all :action}
        :else #{:all})))
  ([hierarchy root-fact-type]
   #(or ((:ancestors hierarchy) %)
      (cond
        (action? %) #{root-fact-type :action}
        :else #{root-fact-type}))))

(defn split-head-body
  "Takes macro body of a deflogical and returns map of :head, :body"
  [rule]
  (let [[head [sep & body]] (split-with #(not= ':- %) rule)]
    {:body body
     :head (first head)}))


;; TODO. Find right ns fns
;(defn unmap-all-rule-nses [nses]
;  (doseq [[k _] (ns-publics *ns*)]
;    (ns-unmap *ns* k)))


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