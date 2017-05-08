(ns libx.schema-fixture
    (:require [libx.util :refer [guid]]
              [libx.schema :refer [attribute]]))

(defn schema []
  [
   (attribute :test-attr/unique
     :db.type/string
     :db/unique :db.unique/identity)

   (attribute :test-attr/one-to-many
     :db.type/string
     :db/cardinality :db.cardinality/many)

   (attribute :test-attr/one-to-one
     :db.type/string
     :db/cardinality :db.cardinality/one)

   (attribute :todo/title
     :db.type/string
     :db/unique :db.unique/identity)

   (attribute :todo/done
     :db.type/boolean)

   (attribute :todo/tags
     :db.type/keyword
     :db/cardinality :db.cardinality/many)])

(def test-schema (schema))
