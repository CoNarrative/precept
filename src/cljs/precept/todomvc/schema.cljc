(ns precept.todomvc.schema
  (:require [precept.util :refer [guid]]
            [precept.schema :refer [attribute]]))

(def schema
  [(attribute :todo/title
     :db.type/string
     :db/unique :db.unique/identity)

   (attribute :todo/done
     :db.type/boolean)])

;; TODO. Rename db schema
(def app-schema schema)

(def client-schema
  [(attribute :todo/visible
     :db.type/boolean)

   (attribute :todo/edit
     :db.type/boolean
     :db/unique :db.unique/identity)])

;
