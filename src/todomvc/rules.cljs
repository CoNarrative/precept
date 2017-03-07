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
  (prn "Ruling")
  (insert! (->VisibleTodos ?todos)))

(defrule show-done
  [Showing (= key :done)]
  [?todos <- Todo (= done true)]
  =>
  (prn "Ruling")
  (insert! (->VisibleTodos ?todos)))

(defrule show-active
  [Showing (= key :active)]
  [?todos <- Todo (= done false)]
  =>
  (prn "Ruling")
  (insert! (->VisibleTodos ?todos)))

(defquery find-showing
  []
  [?showing <- Showing])