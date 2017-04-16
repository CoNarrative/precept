(ns libx.schema
  (:require [libx.spec.sub :as sub]
            [libx.util :refer [guid]]))

(defn by-ident [schema]
  (reduce (fn [acc [k v]] (assoc acc k (first v)))
    {} (group-by :db/ident schema)))

(defn schema-attr? [by-ident attr]
  (contains? by-ident attr))

(defn unique-attr? [by-ident attr]
  (and (schema-attr? by-ident attr)
       (= :db.unique/identity (get-in by-ident [attr :db/unique]))))

(defn unique-value? [by-ident attr]
  (and (schema-attr? by-ident attr)
    (= :db.unique/value (get-in by-ident [attr :db/unique]))))

(defn one-to-one? [by-ident attr]
  (and (schema-attr? by-ident attr)
       (= :db.cardinality/one (get-in by-ident [attr :db/cardinality]))))

(defn one-to-many? [by-ident attr]
  (and (schema-attr? by-ident attr)
       (= :db.cardinality/many (get-in by-ident [attr :db/cardinality]))))

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

(def libx-schema
  [
   (attribute ::sub/request
     :db.type/vector
     :db/unique :db.unique/value)

   (attribute ::sub/response
     :db.type/any
     :db/unique :db.unique/value)])



;
;TODO. Add data readers stub
;(def x)
;[[ ;; stories
;  {:db/id #db/id[:db.part/db]
;   :db/ident :story/title
;   :db/valueType :db.type/string
;   :db/cardinality :db.cardinality/one
;   :db/fulltext true
;   :db/index true
;   :db.install/_attribute :db.part/db}
;  {:db/id #db/id[:db.part/db]
;   :db/ident :story/url
;   :db/valueType :db.type/string
;   :db/cardinality :db.cardinality/one
;   :db/unique :db.unique/identity
;   :db.install/_attribute :db.part/db}
;  {:db/id #db/id[:db.part/db]
;   :db/ident :story/slug
;   :db/valueType :db.type/string
;   :db/cardinality :db.cardinality/one
;   :db.install/_attribute :db.part/db}]
;
; ;; comments
; [{:db/id #db/id[:db.part/db]
;   :db/ident :comments
;   :db/valueType :db.type/ref
;   :db/cardinality :db.cardinality/many
;   :db/isComponent true
;   :db.install/_attribute :db.part/db}
;  {:db/id #db/id[:db.part/db]
;   :db/ident :comment/body
;   :db/valueType :db.type/string
;   :db/cardinality :db.cardinality/one
;   :db.install/_attribute :db.part/db}
;  {:db/id #db/id[:db.part/db]
;   :db/ident :comment/author
;   :db/valueType :db.type/ref
;   :db/cardinality :db.cardinality/one
;   :db.install/_attribute :db.part/db}]
;
; ;; users
; [{:db/id #db/id[:db.part/db]
;   :db/ident :user/firstName
;   :db/index true
;   :db/valueType :db.type/string
;   :db/cardinality :db.cardinality/one
;   :db.install/_attribute :db.part/db}
;  {:db/id #db/id[:db.part/db]
;   :db/ident :user/lastName
;   :db/index true
;   :db/valueType :db.type/string
;   :db/cardinality :db.cardinality/one
;   :db.install/_attribute :db.part/db}
;  {:db/id #db/id[:db.part/db]
;   :db/ident :user/email
;   :db/index true
;   :db/unique :db.unique/identity
;   :db/valueType :db.type/string
;   :db/cardinality :db.cardinality/one
;   :db.install/_attribute :db.part/db}
;  {:db/id #db/id[:db.part/db]
;   :db/ident :user/upVotes
;   :db/valueType :db.type/ref
;   :db/cardinality :db.cardinality/many
;   :db.install/_attribute :db.part/db}]]
;
;(defn validate [schema])
