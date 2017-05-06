(ns libx.schema-fixture
    (:require [libx.util :refer [guid]]
              [libx.schema :refer [attribute]]))

(defn schema []
  [
   (attribute :todo/title
     :db.type/string
     :db/unique :db.unique/identity)

   (attribute :todo/done
     :db.type/boolean)

   (attribute :todo/tags
     :db.type/keyword
     :db/cardinality :db.cardinality/many)])

(def test-schema (schema))
