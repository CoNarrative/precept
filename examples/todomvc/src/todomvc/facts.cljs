(ns todomvc.facts)

(defn todo [id title done]
  (merge
    {:db/id      id
     :todo/title title}
    (when-not (nil? done)
      {:todo/done done})))

(defn visibility-filter [id kw]
  {:db/id                id
   :ui/visibility-filter kw})

(defn mark-all-done-action []
  {:db/id              (random-uuid)
   :ui/toggle-complete :tag})

(def clear-completed-action
  {:db/id              (random-uuid)
   :ui/clear-completed :tag})
