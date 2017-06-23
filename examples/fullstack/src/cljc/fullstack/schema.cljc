(ns fullstack.schema
  (:require [precept.schema :refer [attribute]]))


(defn mk-client-schema []
  [(attribute
    :products/list
    :db.type/vector)])
    ;:db/unique :db.unique/identity)])


(defn mk-db-schema []
  [(attribute :cart-item/product-id
     :db.type/ref
     :db/cardinality :db.cardinality/many)

   (attribute :product/name
     :db.type/string)

   (attribute :product/price
     :db.type/long)

   (attribute :visible-product/id
    :db.type/ref
    :db/cardinality :db.cardinality/many)])


(def db-schema (mk-db-schema))

(def client-schema (mk-client-schema))
