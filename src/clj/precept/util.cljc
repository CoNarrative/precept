(ns precept.util
   (:require [clara.rules :as cr]
             [precept.state :as state]))

(defn trace [& args]
  (comment (apply prn args)))

(defn guid []
  #?(:clj (java.util.UUID/randomUUID)
     :cljs (random-uuid)))

(defn attr-ns [attr]
  (subs (first (clojure.string/split attr "/")) 1))

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
  (->Tuple (first vec)
           (second vec)
           (third vec)
           (nth vec 3 (next-fact-id!))))

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

(defn fact-index-path
  "Returns path to store and access a fact in fact-index according to the fact's cardinality,
  uniqueness"
  [fact]
  (let [ancestry (@state/ancestors-fn (:a fact))]
    (cond
      (ancestry :unique) [:unique (:a fact) (:v fact)]
      (ancestry :one-to-one) [:one-to-one (:e fact) (:a fact)]
      :else [:one-to-many])))

(defn find-in-fact-index
  "Writes value to path in ks. Returns nil if nothing was overwritten. Else returns fact that was
   overwritten."
  [fact ks]
  (if (= ks [:one-to-many])
    nil
    (if-let [existing (get-in @state/fact-index ks)]
      (do
        (swap! state/fact-index assoc-in ks fact)
        existing)
      (do
        (swap! state/fact-index assoc-in ks fact)
        nil))))

(defn remove-from-fact-index
  "Finds in index based on supplied key.
  Compares value of fact to fact that is indexed. If existing value matches
  fact to be removed, removes fact and returns true to indicate removal, Else
  removes nothing and returns false."
  [fact ks]
  (let [existing (get-in @state/fact-index ks)]
    (if (= existing fact)
      (do (swap! state/fact-index dissoc-in ks)
          true)
      false)))

(defn update-unique-index
  "Prepreds :unique to keyseq path in second argument. Overwrites existing value and returns it if
  found."
  [fact ks]
  (if-let [existing (get-in @state/fact-index (into [:unique] ks))]
    (do
      (swap! state/fact-index update-in (into [:unique] ks) (fn [_] fact))
      existing)
    (do
      (swap! state/fact-index assoc-in (into [:unique] ks) fact)
      nil)))

(defn update-index
  "Primary function that updates fact-index. Requires fact to index. Generates key-seq
  to path in index."
  ([fact]
   (update-index fact (fact-index-path fact)))
  ([fact ks]
   (condp = (first ks)
     :one-to-one (find-in-fact-index fact ks)
     :unique (let [removed-from-cardinality (update-index fact [:one-to-one (:e fact) (:a fact)])
                   removed-from-unique (update-unique-index fact (into [] (rest ks)))]
               (when (and (seq removed-from-unique) (not= fact removed-from-unique))
                 (remove-from-fact-index removed-from-unique
                   [:one-to-one (:e removed-from-unique) (:a removed-from-unique)]))
               (when (and (seq removed-from-cardinality) (not= fact removed-from-cardinality))
                 (remove-from-fact-index removed-from-cardinality
                   [:unique (:a removed-from-cardinality) (:v removed-from-cardinality)]))
               (vector removed-from-cardinality removed-from-unique))
    :one-to-many nil)))

(defn insert
  "Inserts facts from outside rule context.
  Accepts [] [[]...]"
  [session facts]
  (let [insertables (insertable facts)
        indexed (remove nil? (flatten (map #(update-index %) insertables)))
        to-insert (into [] (clojure.set/difference (set insertables) (set indexed)))
        to-retract (into [] (clojure.set/difference (set indexed) (set insertables)))
        _ (trace "[insert] to-insert " (mapv vals to-insert))
        _ (trace "[insert] to-retract " (mapv vals to-retract))]
    (if (empty? to-retract)
      (cr/insert-all session to-insert)
      (let [inserted (cr/insert-all session to-insert)]
          (reduce (fn [session fact] (cr/retract session fact))
            inserted
            to-retract)))))

(defn insert-action
  "Inserts hash-map from outside rule context.
  Accepts [e a v] where v is {} with ks that become part of inserted map"
  [session action]
  (trace "[insert-action] : inserting" (tuple-vec->action-hash-map action))
  (cr/insert session (tuple-vec->action-hash-map action)))

(defn insert!
  "Insert facts logically within rule context"
  [facts]
  (let [insertables (insertable facts)
        indexed (remove nil? (flatten (map #(update-index %) insertables)))
        to-insert (into [] (clojure.set/difference (set insertables) (set indexed)))
        to-retract (into [] (clojure.set/difference (set indexed) (set insertables)))]
    (trace "[insert!] : inserting " to-insert)
    (trace "[insert!] : retracting " to-retract)
    (if (empty? to-retract)
      (cr/insert-all! to-insert)
      (do (cr/insert-all! to-insert)
          (doseq [x to-retract] (cr/retract! x))))))

(defn insert-unconditional!
  "Insert facts unconditionally within rule context"
  [facts]
  (let [insertables (insertable facts)
        indexed (remove nil? (flatten (map #(update-index %) insertables)))
        to-insert (into [] (clojure.set/difference (set insertables) (set indexed)))
        to-retract (into [] (clojure.set/difference (set indexed) (set insertables)))]
    (trace "[insert-unconditional!] : inserting " to-insert)
    (trace "[insert-unconditional!] : retracting " to-retract)
    (if (empty? to-retract)
      (cr/insert-all-unconditional! to-insert)
      (do (cr/insert-all-unconditional! to-insert)
          (doseq [x to-retract] (cr/retract! x))))))

(defn action-insert! [m]
  (insert-unconditional! (gen-Tuples-from-map m)))

(defn retract!
  "Wrapper around Clara's `retract!`.
  To be used within RHS of rule only. Converts all input to Facts"
  [facts]
  (let [insertables (insertable facts)
        _ (trace "[retract!] :" insertables)]
    (doseq [x insertables]
      (cr/retract! x)
      (remove-from-fact-index x (fact-index-path x)))))

(defn retract
  "Retract from outside rule context.
  [] [[]..]"
  [session facts]
  (let [insertables (insertable facts)
        _ (doseq [x insertables] (remove-from-fact-index x (fact-index-path x)))
        _ (trace "[retract] : " insertables)]
    (reduce (fn [s fact] (cr/retract s fact))
      session
      insertables)))

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

;; Unclear what Clara expects. Could be -1 0 1 but their default sort-fn is >
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

(defn make-ancestors-fn
  "To be used when defining a session. Stored in atom for auto truth maintenance
  and schema enforcement."
  ([]
   (let [cr-ancestors-fn (fn [_] #{:all :one-to-one})]
     (reset! state/ancestors-fn (memoize cr-ancestors-fn))
     cr-ancestors-fn))
  ([hierarchy]
   (let [cr-ancestors-fn #(or ((:ancestors hierarchy) %)
                              #{:all :one-to-one})]
      (reset! state/ancestors-fn (memoize cr-ancestors-fn))
      cr-ancestors-fn))
  ([hierarchy root-fact-type]
   (let [cr-ancestors-fn #(or ((:ancestors hierarchy) %)
                             #{root-fact-type :one-to-one})]
      (reset! state/ancestors-fn (memoize cr-ancestors-fn))
      cr-ancestors-fn)))

(defn split-head-body
  "Takes macro body of a deflogical and returns map of :head, :body"
  [rule]
  (let [[head [sep & body]] (split-with #(not= ':- %) rule)]
    {:body body
     :head (first head)}))

(defn find-sub-by-name [name]
  (second
    (first
      (filter
        (fn [[id sub]] (= name (:name sub)))
        (:subscriptions @state/state)))))


;; TODO. Find right ns fns
;(defn unmap-all-rule-nses [nses]
;  (doseq [[k _] (ns-publics *ns*)]
;    (ns-unmap *ns* k)))
