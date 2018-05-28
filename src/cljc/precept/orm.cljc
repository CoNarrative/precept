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
  [op-type *tree ancestry tuples preserve-fact-id?]
  (let [value-fn #_(if preserve-fact-id? #(select-keys % [:v :t]) :v)
                   (if preserve-fact-id? #(zipmap [:v :t] (nthrest % 2))
                                         #(nth % 2))]
    (doseq [[e a v t :as tuple] tuples]
      (if (not= a ::sub/response)
        (let [transform-fn (get-transform-fn ancestry op-type a [e a] (value-fn tuple))]
          (transform-fn *tree))
        (doseq [[sub-key sub-val] v]
          ;; Because we allow non-scalar values such as maps
          ;; we must perform this step to convert {:sub-name [Tuples]} to maps with
          ;; :db/id
          (let [maps (if (util/any-Tuple? sub-val)
                       (util/Tuples->maps sub-val)
                       sub-val)
                leaf (if preserve-fact-id? {:v maps :t t} maps)
                transform-fn (get-transform-fn ancestry op-type sub-key [e a sub-key] leaf)]
            (transform-fn *tree)))))))

; TODO. Requires tuple vectors. Callers are always having to convert to this.
; Accept Tuples/maps
; TODO. If receive add and remove, process removals first
(defn update-tree!
  "-  *tree - atom for ORM tree
  - ancestry - set, :ancestors key of derived Clojure hierarchy
  - ops - precept.listeners/vec-ops results keyed by :add, :remove"
  [*tree ancestry {:keys [add remove preserve-fact-id?] :as ops}]
  (doseq [[op-type facts] ops]
    (when facts
      (apply-ops! op-type *tree ancestry facts preserve-fact-id?)))
  *tree)
