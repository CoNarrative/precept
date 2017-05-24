(ns precept.schema-fixture
    (:require [precept.util :refer [guid]]
              [precept.spec.test :as test]
              [precept.schema :refer [attribute]]))

(defn schema []
  [
   (attribute ::test/unique-identity
     :db.type/string
     :db/unique :db.unique/identity)

   (attribute ::test/one-to-many
     :db.type/string
     :db/cardinality :db.cardinality/many)

   (attribute ::test/one-to-one
     :db.type/string
     :db/cardinality :db.cardinality/one)

   (attribute ::test/unique-value
     :db.type/string
     :db/unique :db.unique/value
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
