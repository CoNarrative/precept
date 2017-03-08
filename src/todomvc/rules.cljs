(ns todomvc.rules
  (:require-macros [clara.macros :refer [defrule defquery defsession]])
  (:require [clara.rules :refer [insert insert! insert-all! insert-all-unconditional!
                                 retract! query fire-rules]]
            [clara.rules.accumulators :as acc]))



(defrecord Todo [id title done])

(defrecord Showing [key])

(defrecord VisibleTodos [todos])

(defrecord ToggleComplete [])

(defrule show-all
  [Showing (= key :all)]
  [?todos <- (acc/all) :from [Todo]]
  =>
  (prn "show-all rule fired" ?todos)
  (insert! (->VisibleTodos ?todos)))

(defrule show-done
  [Showing (= key :done)]
  [?todos <- (acc/all) :from [Todo (= done true)]]
  =>
  (prn "show-done rule fired")
  (insert! (->VisibleTodos ?todos)))

(defrule show-active
  [Showing (= key :active)]
  [?todos <- (acc/all) :from [Todo (= done false)]]
  =>
  (prn "show-active rule fired")
  (insert! (->VisibleTodos ?todos)))

(defrule toggle-all-complete
  [?toggle <- ToggleComplete]
  [?todos <- (acc/all) :from [Todo]]
  =>
  (prn "toggle-all-complete rule fired")
  (prn "inserting" (map #(update % :done not) ?todos))
  (retract! ?toggle)
  (apply retract! ?todos)
  (insert-all-unconditional! (map #(update % :done not) ?todos)))

(defquery find-showing
  []
  [?showing <- Showing])

(defquery find-visible-todos
  []
  [?visible-todos <- VisibleTodos])

(defquery find-todos
  []
  [?todos <- (acc/all) :from [Todo]])

(defquery find-todo
  [:?id]
  [?todo <- Todo (= id ?id)])

(defquery find-max-id
  []
  [?id <- (acc/max :id) :from [Todo]])

(defquery find-all-done
  []
  [?todos <- (acc/all) :from [Todo (= done true)]])

(defquery find-done-count
  []
  [?count <- (acc/count) :from [Todo (= done true)]])

(defsession todos 'todomvc.rules)
