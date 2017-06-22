(ns precept.schema
    (:require [precept.spec.sub :as sub]
              [precept.query :as q]
              [precept.util :refer [guid]]
              [precept.state :as state]))

(defn by-ident [schema]
  (reduce (fn [acc [k v]] (assoc acc k (first v)))
    {} (group-by :db/ident schema)))

(defn schema-attr? [by-ident attr]
  (contains? by-ident attr))

(defn unique-attr? [by-ident attr]
  (and (schema-attr? by-ident attr)
       (= (or :db.unique/identity
              :db.unique/value)
          (get-in by-ident [attr :db/unique]))))

(defn one-to-many? [by-ident attr]
  (and (schema-attr? by-ident attr)
       (= :db.cardinality/many (get-in by-ident [attr :db/cardinality]))))

(defn unique-attrs [schema tuples]
  (reduce (fn [acc cur]
            (if (unique-attr? schema (second cur))
              (conj acc (second cur))
              acc))
    [] tuples))

(defn unique-facts [session unique-attrs]
  (mapcat #(q/facts-where session %) unique-attrs))

(defn attribute
  "Creates a Datomic schema entry for an attribute. Cardinality defaults to
  one-to-one. Generates UUID for :db/id."
  [ident type & {:as opts}]
  (merge {:db/id        (guid)
          :db/ident     ident
          :db/valueType type}
    {:db/cardinality :db.cardinality/one}
    opts))

(defn enum
  [ident & {:as fields}]
  (merge {:db/id    (guid)
          :db/ident ident}
    fields))

(def precept-schema
  [(attribute ::sub/request
     :db.type/vector
     :db/unique :db.unique/identity)

   (attribute ::sub/response
     :db.type/any
     :db/unique :db.unique/identity)

   (attribute :entities/eid
     :db.type/any
     :db/cardinality :db.cardinality/many)

   (attribute :entities/entity
     :db.type/any
     :db/cardinality :db.cardinality/many)])

(defn schema->hierarchy
  "Creates a hierarchy from a Datomic schmea by cardinality and uniqueness. Used by
  implementation to enforce both."
  [schema]
  (let [h (atom (make-hierarchy))
        cardinality (group-by :db/cardinality schema)
        uniqueness (group-by :db/unique schema)
        unique-identity (map :db/ident (:db.unique/identity uniqueness))
        unique-value (map :db/ident (:db.unique/value uniqueness))
        one-to-manys (map :db/ident (:db.cardinality/many cardinality))
        one-to-ones (clojure.set/difference (into #{} (map :db/ident schema)) (set one-to-manys))]
    (doseq [x one-to-ones] (swap! h derive x :one-to-one))
    (doseq [x one-to-manys] (swap! h derive x :one-to-many))
    (doseq [x unique-identity] (swap! h derive x :unique-identity))
    (doseq [x unique-value] (swap! h derive x :unique-value))
    (swap! h derive :one-to-one :all)
    (swap! h derive :one-to-many :all)
    (swap! h derive :unique-value :one-to-one)
    (swap! h derive :unique-identity :one-to-one)
    (reset! state/session-hierarchy h)
    @h))

(defn init!
  "Stores schemas and returns hierarchy for provided schemas and Precept's internal schema"
  [{:keys [db-schema client-schema]}]
  (let [schemas (remove nil? (concat db-schema client-schema precept-schema))]
    (swap! state/schemas assoc :db db-schema :client client-schema)
    (schema->hierarchy schemas)))

(defn a-v-pairs->tuples
  [e avs]
  (reduce
    (fn reduce-avs [acc2 [a v]]
      (if (and (= ((@state/ancestors-fn a) :one-to-many))
               (coll? v))
        (into acc2 (mapv #(vector e a %) v))
        (conj acc2 (vector e a v))))
    []
    avs))

(defn store->tuples
  [attr-set]
  (reduce
    (fn reduce-fact-type [acc [e avs]]
      (if (contains? attr-set (ffirst avs))
        (into acc (a-v-pairs->tuples e avs))
        acc))
    []
    @state/store))

(defn persistent-attrs []
  (set (keys (by-ident (:db @state/schemas)))))

(defn persistent-facts []
  "Retrieves persistent facts from view model, excluding subscriptions.

  Returns vector of eav tuples."
    (store->tuples (persistent-attrs)))
