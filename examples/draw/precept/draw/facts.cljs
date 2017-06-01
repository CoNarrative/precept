(ns precept.draw.facts)

(defn visibility-filter [v] [:global :visibility-filter v])

(defn entry [v] [:global :entry/title v])

(defn done-count [v] [:global :done-count v])

(defn active-count [v] [:global :active-count v])

(defn todo-edit [e v] [e :todo/edit v])

(defn todo [title]
  (let [id (random-uuid)]
    [[id :todo/title title]
     [id :todo/done false]]))

