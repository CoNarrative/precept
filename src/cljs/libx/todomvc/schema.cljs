(ns libx.todomvc.schema)

(defn attribute [ident type & {:as opts}]
  (merge {:db/id        (random-uuid)
          :db/ident     ident
          :db/valueType type}
    {:db/cardinality :db.cardinality/one}
    opts))

(defn enum [ident & {:as fields}]
  (merge {:db/id    (random-uuid)
          :db/ident ident}
    fields))

(defn schema []
  [
   ; Todos
   (attribute :todo/title
     :db.type/string)

   (attribute :todo/visible
     :db.type/boolean)

   (attribute :todo/done
     :db.type/keyword)

   ; UI
   (attribute :ui/toggle-complete
     :db.type/enum ;;tag
     :db/unique :db.unique/identity)

   (attribute :ui/visibility-filter
     :db.type/enum ;;tag
     :db/unique :db.unique/identity)

   (attribute :done-count
     :db.type/enum ;;tag
     :db/unique :db.unique/identity)

   (attribute :active-count
     :db.type/enum ;;tag
     :db/unique :db.unique/identity)])


(def app-schema (schema))
