(ns todomvc.schema
    (:require [precept.util :refer [guid]]
              [precept.schema :refer [attribute]]
              [precept.state :as state]))


(defn gen-db-schema []
  [(attribute :todo/title
     :db.type/string
     :db/unique :db.unique/identity)

   (attribute :todo/done
     :db.type/boolean)])

(defn gen-client-schema []
  [(attribute :todo/visible
     :db.type/boolean)

   (attribute :todo/edit
     :db.type/boolean
     :db/unique :db.unique/identity)])

(def db-schema (gen-db-schema))
(def client-schema (gen-client-schema))
