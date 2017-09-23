(ns precept.util
   (:require [clara.rules :as cr]
             [clojure.spec.alpha :as s]
             [clojure.core.async :as async]
             [precept.schema :as schema]
             [precept.state :as state]
             [precept.spec.fact :as fact]
             [precept.spec.rulegen :as rulegen]
             [precept.spec.error :as err]))

(declare update-index!)

(defn trace [& args]
  (comment (apply prn args)))

(defn guid []
  #?(:clj (java.util.UUID/randomUUID)
     :cljs (random-uuid)))

(defn unknown-ancestry-error [fact ancestry]
  (throw (ex-info (str "Unknown fact type. Expected one of #{:one-to-one :one-to-many
        :unique-identity :unique-value} but received " ancestry)
           {:fact fact
            :fact-ancestry ancestry})))

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

;(defn print-format-Tuple [rec]
;  (let [vec (mapv
;              (fn [[k v]]
;                (if (= Tuple (type v))
;                  (symbol (print-format-Tuple v))
;                  v))
;              rec)]
;   (str "Tuple" vec "\n")))
;
;
;#?(:clj
;    (defmethod print-method Tuple [v ^java.io.Writer w]
;        (.write w (print-format-Tuple v))))
;
;#?(:cljs
;    (extend-protocol IPrintWithWriter
;      precept.util/Tuple
;      (-pr-writer [{:keys [e a v t]} writer _]
;        (write-all writer "\n[" (subs (str e) 0 6) " " a " " v " " t "]\n"))))

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

(defn record->map [x]
  (clojure.walk/postwalk #(if (record? %) (into {} %) %) x))

(defn vec->record [vec]
  (->Tuple (first vec)
           (second vec)
           (third vec)
           (nth vec 3 (next-fact-id!))))

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

(defn map-of-refs?
  "Returns true if every k is db.type/ref according to a schema"
  [schemas x]
  (and (every? map? x)
       (every?
         (comp first
               #(schema/ref? schemas %))
        x)))

;; TODO. Parent should have vec of refs for one-to-many
(defn entity-map->Tuples
  "Transforms entity map to Tuple record
  {a1 v1 a2 v2 :db/id eid} -> [(Tuple eid a1 v1 t)...]"
  [m]
  (reduce
    (fn [acc [k v]]
      (let [one-to-many? ((@state/ancestors-fn k) :one-to-many)]
        (when (and one-to-many? (not (coll? v)))
          (throw (ex-info (str "Attribute " k "is marked :one-to-many but we received an entity map
          where its value is " v ". One to many attributes in entity maps must be collections.")
                   {:attribute k :value v :schema-entry (@state/ancestors-fn k)})))
        (if one-to-many?
          (concat acc (map #(->Tuple (:db/id m) k % (next-fact-id!)) v))
          (conj acc (->Tuple (:db/id m) k v (next-fact-id!))))))
    []
    (dissoc m :db/id)))
;; TODO. Issue #83
;"Transforms entity map to Tuple record. Accepts attributes with values that are collections
;  and generates their eids as uuids.
;  Throws error if an attribute with a value that is a collection is not `:one-to-many` according to
;  a provided schema.
;  {:db/id 123 :a [{:nested 1 :entity 2}...] ->
;    [(Tuple 123 :a <uuid-ref>) (Tuple <uuid-ref> :nested 1) (Tuple <uuid-ref> :entity 2)]
;  {:db/id eid :a1 v1 :a2 v2 } -> [(Tuple eid a1 v1 t)...]"
        ;(cond
        ;  (and one-to-many? (map-of-refs? (vals @state/schemas) v))
        ;  (let [fact-vec (mapv #(->Tuple (guid) k % (next-fact-id!)) v)
        ;        x (->Tuple (:db/id m) k (mapv :e fact-vec) (next-fact-id!))]
        ;    (concat acc (conj fact-vec x)#_(mapv #(->Tuple (guid) k % (next-fact-id!)) v)))
        ;  :default
        ;  (conj acc (->Tuple (:db/id m) k v (next-fact-id!))))))

(defn insertable
  "Arguments can be any mixture of vectors and records
  Ensures [], [[]...], Tuple, '(Tuple ...) conform to Tuple record instances."
  [x]
  (cond
    (record? x) (vector x)
    (and (coll? x) (record? (first x))) (into [] x)
    (s/valid? ::fact/tuple x) (vector (vec->record x))
    (s/valid? (s/coll-of ::fact/tuple) x) (mapv vec->record x)
    (s/valid? ::fact/entity-map x) (entity-map->Tuples x)
    (s/valid? (s/coll-of ::fact/entity-map) x) (into [] (mapcat entity-map->Tuples x))))

(defn fact-index-path
  "Returns path to store and access a fact in fact-index according to the fact's cardinality,
  uniqueness"
  [fact]
  (let [ancestry (@state/ancestors-fn (:a fact))]
    (cond
      (ancestry :unique-identity) [:unique (:a fact) (:v fact)]
      (ancestry :unique-value) [:unique (:a fact) (:v fact)]
      (ancestry :one-to-one) [:one-to-one (:e fact) (:a fact)]
      (ancestry :one-to-many) [:one-to-many]
      :default (unknown-ancestry-error fact ancestry))))

(defn fact-index-paths
  "Returns all index paths for a given fact as [[]...]"
  [fact]
  (let [ancestry (@state/ancestors-fn (:a fact))]
    (cond
      (ancestry :unique-identity) [[:unique (:a fact) (:v fact)]
                                   [:one-to-one (:e fact) (:a fact)]]
      (ancestry :unique-value) [[:unique (:a fact) (:v fact)]
                                [:one-to-one (:e fact) (:a fact)]]
      (ancestry :one-to-one) [[:one-to-one (:e fact) (:a fact)]]
      (ancestry :one-to-many) :one-to-many
      :default (unknown-ancestry-error fact ancestry))))

(defn schema-enforcement-dto [inserted retracted index-path]
  {:type :schema-enforcement
   :inserted (into {} inserted)
   :retracted (into {} retracted)
   :index-path index-path})

;;TODO. index-cardinality! or add to cardinality
(defn upsert-fact-index!
  "Writes value to path in ks. Returns existing fact to retract if overwriting."
  [fact ks]
  (if-let [existing (get-in @state/fact-index ks)]
    (do
      (when (:connected? @state/*devtools)
        (async/put! (:event-sink @state/*devtools) (schema-enforcement-dto fact existing ks)))
      (swap! state/fact-index assoc-in ks fact)
      {:insert fact :retract existing})
    (do
      (swap! state/fact-index assoc-in ks fact)
      {:insert fact})))

(defn remove-fact-from-index!
  "Removes fact from all indexed locations according to schema if the indexed value is identical
  to the `fact` argument.
  Returns `[bool...]` indicating successful removal from each indexed location."
  ([fact] (remove-fact-from-index! fact (fact-index-paths fact)))
  ([fact paths]
   (if (= paths :one-to-many) ; Nothing to be done
     [true]
     (mapv
       (fn [ks]
         (if-let [exact-match (= fact (get-in @state/fact-index ks))]
           (do (trace "Removing exact match" exact-match)
               (boolean (swap! state/fact-index dissoc-in ks)))))
       paths))))

;;TODO. Rename remove-from-index-path! (can be unique or card)
;;TODO. Investigate whether this fn should remove iff exact match vs. e-a match
(defn remove-from-fact-index!
  "Removes fact from single path in index, returning a retract instruction if an exact match
  was found"
  [fact ks]
  (let [existing (get-in @state/fact-index ks)]
    (if (= existing fact)
      (do (swap! state/fact-index dissoc-in ks)
          {:retract fact})
      {:retract nil})))

;; TODO. Rename index-unique!
(defn add-to-unique-index!
  "Prepreds :unique to keyseq path in second argument. Overwrites existing value and returns it if
  found."
  [fact ks]
  (let [unique-path (into [:unique] ks)
        existing (get-in @state/fact-index unique-path)]
    (if existing
      (do
        (swap! state/fact-index dissoc-in unique-path) ;; do we need this?
        (swap! state/fact-index update-in unique-path (fn [_] fact))
        {:insert fact :retract existing})
      (do
        (swap! state/fact-index assoc-in (into [:unique] ks) fact)
        {:insert fact :retract nil}))))

; TODO. Separate unique type operations into own functions
(defn check-unique-conflict [fact ks]
  (let [existing (get-in @state/fact-index [:unique (:a fact) (:v fact)])
        existing-value (get-in @state/fact-index [:one-to-one (:e fact) (:a fact)])
        unique-type (@state/ancestors-fn (:a fact))]
    (if (or (and (unique-type :unique-value)
                 (or existing-value
                     (and existing (not= (:e fact) (:e existing)))))
            (and (unique-type :unique-identity)
                 existing (not= (:e fact) (:e existing))))
       (let [id (guid)]
         {:insert
          (mapv vec->record
           [[id ::err/type :unique-conflict]
            [id ::err/existing-fact (or existing existing-value)]
            [id ::err/failed-insert fact]])})
      nil)))

(defn upsert-unique-index! [fact ks]
  (let [from-unique (add-to-unique-index! fact (into [] (rest ks)))
        from-cardinality (update-index! fact [:one-to-one (:e fact) (:a fact)])]
    (when (and (:retract from-unique)
            (not= fact (:retract from-unique)))
      (remove-from-fact-index!
        (:retract from-unique)
        [:one-to-one (:e (:retract from-unique) (:a (:retract from-unique)))]))
    (when (and (:retract from-cardinality)
            (not= fact (:retract from-cardinality)))
      (remove-from-fact-index!
        (:retract from-cardinality)
        [:unique (:a (:retract from-cardinality))
                 (:v (:retract from-cardinality))]))
    {:insert (first (distinct (remove nil? (map :insert [from-cardinality from-unique]))))
     :retract (first (distinct (remove nil? (map :retract [from-cardinality from-unique]))))}))

(defn update-unique-index! [fact ks]
  (let [unique-conflict (check-unique-conflict fact ks)]
    (or unique-conflict
        (upsert-unique-index! fact ks))))

(defn update-index!
  "Primary function that updates fact-index. Requires fact to index. Generates key-seq
  to path in index."
  ([fact]
   (update-index! fact (fact-index-path fact)))
  ([fact ks]
   (condp = (first ks)
     :one-to-one (upsert-fact-index! fact ks)
     :unique (update-unique-index! fact ks)
     :one-to-many {:insert fact})))

(defn conform-insertions-and-retractions! [facts]
  (let [insertables (insertable facts)
        indexed (map update-index! insertables)
        to-insert (vec (flatten (remove nil? (map :insert indexed))))
        to-retract (vec (flatten (remove nil? (map :retract indexed))))]
    (vector to-insert to-retract)))

(defn insert
  "Inserts facts from outside rule context.
  Accepts `[e a v]`, `[[e a v]...]`, `{}`, `[{}...]`, where `{}` is a Datomic-style entity map"
  [session facts]
  (let [[to-insert to-retract] (conform-insertions-and-retractions! facts)
        _ (trace "[insert] facts" facts)
        _ (trace "[insert] to-insert " to-insert)
        _ (trace "[insert] to-retract " to-retract)]
    (if (empty? to-retract)
      (do (swap! state/unconditional-inserts clojure.set/union (set to-insert))
          (cr/insert-all session to-insert))
      (let [session-with-inserts (cr/insert-all session to-insert)]
        (do (swap! state/unconditional-inserts clojure.set/union (set to-insert))
            (swap! state/unconditional-inserts clojure.set/difference (set to-retract)))
        (reduce (fn [session fact] (cr/retract session fact))
          session-with-inserts
          to-retract)))))

#?(:clj
    (defn conflicting-logical-fact-error [facts to-insert to-retract]
      (binding [*out* *err*]
        (println "Conflicting logical fact!" to-insert " is blocked by " to-retract))))
#?(:cljs
    (defn conflicting-logical-fact-error [facts to-insert to-retract]
      (throw (ex-info "Conflicting logical fact. You may have rules whose conditions are not
                  mutually exclusive that insert! the same e-a consequence. The conditions for logically
                  inserting an e-a pair must be exclusive if the attribute is one-to-one. If you have two
                  identical accumulators and you are seeing this error, create a separate rule that inserts a
                  fact with the accumulator's result and replace the duplicate accumulators with expressions
                  that match on that fact."
               {:arguments facts
                :attempted-insert to-insert
                :blocking-fact to-retract}))))

(defn insert!
  "Insert facts logically within rule context"
  [facts]
  (let [[to-insert to-retract] (conform-insertions-and-retractions! facts)]
    (trace "[insert!] : inserting " to-insert)
    (trace "[insert!] : conflicting " to-retract)
    (if (empty? to-retract)
      (cr/insert-all! to-insert)
      (conflicting-logical-fact-error facts to-insert to-retract))))

(defn insert-unconditional!
  "Insert facts unconditionally within rule context"
  [facts]
  (let [[to-insert to-retract] (conform-insertions-and-retractions! facts)]
    (trace "[insert-unconditional!] : inserting " to-insert)
    (trace "[insert-unconditional!] : retracting " to-retract)
    (if (empty? to-retract)
      (do (swap! state/unconditional-inserts clojure.set/union (set to-insert))
          (cr/insert-all-unconditional! to-insert))
      (do (cr/insert-all-unconditional! to-insert)
          (swap! state/unconditional-inserts clojure.set/difference (set to-retract))
          (doseq [x to-retract]
            (cr/retract! x))))))

(defn retract!
  "Wrapper around Clara's `retract!`. Use within RHS of rule only.
  Requires a fact that includes a fact-id produced by matching on a whole fact.
  e.g. `?fact` in `[?fact <- [?e :attr ?v]`"
  [facts]
  (let [insertables (insertable facts)
        _ (trace "[retract!] :" insertables)]
    (do (swap! state/unconditional-inserts clojure.set/difference (set insertables))
        (doseq [x insertables]
          (remove-fact-from-index! x)
          (cr/retract! x)))))

(defn retract
  "Retract from outside rule context."
  [session facts]
  (let [insertables (insertable facts)
        _ (trace "[retract] : " insertables)]
    (reduce
      (fn [s fact]
        (do (swap! state/unconditional-inserts disj fact)
            (remove-fact-from-index! fact)
            (cr/retract s fact)))
      session
      insertables)))

(defn any-Tuple? [x]
  (or (= Tuple (type x))
      (and (coll? x) (some any-Tuple? x))))

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

(defn Tuples->maps
  ([xs] (Tuples->maps xs @state/ancestors-fn))
  ([xs ancestry]
   (letfn [(recur-or-val [ys] (if (any-Tuple? ys) (Tuples->maps ys) ys))]
     (if (record? (ffirst xs))
       (into [] (mapcat Tuples->maps xs))
       (if (= Tuple (type xs))
         {:db/id (:e xs) (:a xs) (recur-or-val (:v xs))}
         (let [keyed (reduce
                       (fn [m {:keys [e a v]}]
                         (if (contains? (ancestry a) :one-to-many)
                           (update-in m [e a] conj (recur-or-val v))
                           (assoc-in m [e a] (recur-or-val v))))
                       {} xs)]
           (mapv (fn [[eid m]] (assoc m :db/id eid)) keyed)))))))

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
  `:super` - boolean
  `:group` - keyword
  `:salience` - number
   Rules marked `:super` are given the highest priority."
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

(defn make-ancestors-fn
  "Returns a set. To be used when defining a session. Stored in atom for auto truth maintenance
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
  "Takes macro body of a define and returns map of :head, :body"
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

(def impl-fact-nses #{"precept.spec.rulegen"})

(defn impl-fact? [a]
  (or
    (contains? impl-fact-nses (namespace a))
    (s/valid? ::rulegen/request a)))

(defn remove-impl-attrs [xs]
  (remove #(some-> (:a %) (impl-fact?)) xs))

(defn rules-in-ns
  [ns-sym]
  (let [rule-syms (map (comp symbol :name)
                    (filter #(= (:ns %) ns-sym)
                      (vals @state/rules)))]
    (set rule-syms)))


