(ns todomvc.schema)


(defn attribute [ident type & {:as opts}]
  (merge {:db/id        (random-uuid)
          :db/ident     ident
          :db/valueType type}
    ;:db.install/_attribute :db.part/db}
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

   ; optional -- trying AI approach (if it doesn't exist it's false)
   (attribute :todo/status
     :db.type/keyword)

   ; UI
   (attribute :ui/toggle-complete
     :db.type/boolean)

   (attribute :ui/visibility-filter
     :db.type/enum)
   (enum :all)
   (enum :active)
   (enum :done)])

