(ns libx.todomvc.schema
  (:require [libx.util :refer [guid]]))

(defn attribute [ident type & {:as opts}]
  (merge {:db/id        (guid)
          :db/ident     ident
          :db/valueType type}
    {:db/cardinality :db.cardinality/one}
    opts))

(defn enum [ident & {:as fields}]
  (merge {:db/id    (guid)
          :db/ident ident}
    fields))

(defn schema []
  [
   (attribute :todo/title
     :db.type/string
     :db/unique :db.unique/value)

   (attribute :todo/edit
     :db.type/string
     :db/unique :db.unique/identity)

   (attribute :todo/visible
     :db.type/keyword)

   (attribute :todo/done
     :db.type/keyword)

   (attribute :entry/title
     :db.type/string
     :db/unique :db.unique/identity)

   (attribute :input/key-code
     :db.type/long
     :db/unique :db.unique/identity)

   (attribute :ui/visibility-filter
     :db.type/keyword
     :db/unique :db.unique/identity)

   (attribute :done-count
     :db.type/enum ;;tag
     :db/unique :db.unique/identity)

   (attribute :active-count
     :db.type/enum ;;tag
     :db/unique :db.unique/identity)])


(def app-schema (schema))
