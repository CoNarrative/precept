(ns precept.todomvc.facts)

(defn visibility-filter [v] [:global :visibility-filter v])

(defn entry [v] [:global :entry/title v])

(defn done-count [v] [:global :done-count v])

(defn active-count [v] [:global :active-count v])

(defn todo [title]
  {:db/id (random-uuid)
   :todo/title title
   :todo/done false})
