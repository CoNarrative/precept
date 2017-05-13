(ns precept.todomvc.schema
  (:require [precept.util :refer [guid]]
            [precept.schema :refer [attribute]]))

(defn schema []
  [
   (attribute :todo/title
     :db.type/string
     :db/unique :db.unique/identity)

   (attribute :todo/done
     :db.type/boolean)])

(def app-schema (schema))
