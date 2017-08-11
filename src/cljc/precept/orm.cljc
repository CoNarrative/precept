(ns precept.orm
  (:require [precept.spec.sub :as sub]
            [precept.util :as util]))


(defmulti get-transform-fn
  (fn [ancestry op a _ _]
    [op (first (clojure.set/intersection
                 #{:one-to-many :one-to-one}
                 (ancestry a)))]))

(defmethod get-transform-fn [:add :one-to-one] [_ _ _ ks v]
  #(swap! % assoc-in ks v))

(defmethod get-transform-fn [:add :one-to-many] [_ _ _ ks v]
  #(swap! % update-in ks conj v))

(defmethod get-transform-fn [:remove :one-to-one] [_ _ _ ks _]
  #(swap! % util/dissoc-in ks))

(defmethod get-transform-fn [:remove :one-to-many] [_ _ _ ks v]
  (fn [*tree]
    (let [prop (get-in @*tree ks)
          applied (remove #(= v %) prop)]
      (if (empty? applied)
        (swap! *tree util/dissoc-in ks)
        (swap! *tree assoc-in ks applied)))))

(defn apply-ops!
  [op-type *tree ancestry tuples]
  (doseq [[e a v] tuples]
    (if (not= a ::sub/response)
      (let [transform-fn (get-transform-fn ancestry op-type a [e a] v)]
        (transform-fn *tree))
      (doseq [[sub-a sub-v] v]
        ;; Because we allow non-scalar values such as maps
        ;; we must perform this step to convert {:sub-name [Tuples]} to maps with
        ;; :db/id
        (let [maps (if (util/any-Tuple? sub-v)
                     (util/Tuples->maps sub-v)
                     sub-v)
              transform-fn (get-transform-fn ancestry op-type sub-a [e a sub-a] maps)]
          (transform-fn *tree))))))

(defn update-tree!
  "-  *tree - atom for ORM tree
  - ancestry - set, :ancestors key of derived Clojure hierarchy
  - ops - precept.listeners/vec-ops results keyed by :add, :remove"
  [*tree ancestry {:keys [add remove] :as ops}]
  (doseq [[op-type facts] ops]
    (when facts
      (apply-ops! op-type *tree ancestry facts)))
  *tree)
