(ns story.schema
  (:require [libx.util :refer [guid]]
            [libx.schema :refer [attribute]]))

(defn schema []
  [
   (attribute :part/title
     :db.type/string
     :db/unique :db.unique/identity)])

(def app-schema (schema))
