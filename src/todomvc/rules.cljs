(ns todomvc.rules
  (:require-macros [clara.macros :refer [defrule defquery defsession]])
  (:require [clara.rules :refer [insert! fire-rules]]))


(defsession todos 'todomvc.rules)

(defrecord Todo [id title done])

(defrecord Showing [key])

(defrecord VisibleTodos [todos])

(defrule show-all
  [Showing (= key :all)]
  [?todos <- Todo]
  =>
  (insert! (->VisibleTodos ?todos)))

(defrule show-done
  [Showing (= key :done)]
  [?todos <- Todo (= done true)]
  =>
  (insert! (->VisibleTodos ?todos)))

(defrule show-active
  [Showing (= key :active)]
  [?todos <- Todo (= done false)]
  =>
  (insert! (->VisibleTodos ?todos)))
