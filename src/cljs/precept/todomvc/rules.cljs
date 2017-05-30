(ns precept.todomvc.rules
  (:require-macros [precept.dsl :refer [<- entity entities]])
  (:require [precept.accumulators :as acc]
            [precept.spec.error :as err]
            [precept.util :refer [insert! insert-unconditional! retract! guid] :as util]
            [precept.tuplerules :refer-macros [define defsub session rule]]
            [precept.todomvc.facts :refer [todo entry done-count active-count visibility-filter]]))


(rule save-edit
  {:group :action}
  [[_ :todo/save-edit ?e]]
  [?edit <- [?e :todo/edit ?v]]
  =>
  (retract! ?edit)
  (insert-unconditional! [?e :todo/title ?v]))

(rule clear-completed
  {:group :action}
  [[_ :clear-completed]]
  [[?e :todo/done true]]
  [(<- ?done-entity (entity ?e))]
  =>
  (retract! ?done-entity))

(rule complete-all
  {:group :action}
  [[_ :mark-all-done]]
  [[?e :todo/done false]]
  =>
  (insert-unconditional! [?e :todo/done true]))

(rule save-edit-when-enter-pressed
  {:group :action}
  [[_ :input/key-code 13]]
  [[?e :todo/edit]]
  =>
  (insert! [:transient :todo/save-edit ?e]))

(rule create-todo-when-enter-pressed
  {:group :action}
  [[_ :input/key-code 13]]
  [[_ :entry/title]]
  =>
  (insert! [:transient :todo/create :tag]))

(rule create-todo
  {:group :action}
  [[_ :todo/create]]
  [?entry <- [_ :entry/title ?v]]
  =>
  (retract! ?entry)
  (insert-unconditional! (todo ?v)))

(define [?e :todo/visible true] :-
  [:or [:and [_ :visibility-filter :all] [?e :todo/title]]
       [:and [_ :visibility-filter :done] [?e :todo/done true]]
       [:and [_ :visibility-filter :active] [?e :todo/done false]]])

(rule insert-done-count
  [?n <- (acc/count) :from [_ :todo/done true]]
  =>
  (insert-unconditional! (done-count ?n)))

(rule insert-active-count
  [[_ :done-count ?done]]
  [?total <- (acc/count) :from [:todo/title]]
  =>
  (insert-unconditional! (active-count (- ?total ?done))))

(defsub :task-list
  [?eids <- (acc/by-fact-id :e) :from [:todo/visible]]
  [(<- ?visible-todos (entities ?eids))]
  [[_ :active-count ?active-count]]
  =>
  {:visible-todos ?visible-todos
   :all-complete? (= 0 ?active-count)})

(defsub :task-entry
  [[?e :entry/title ?v]]
  =>
  {:db/id ?e :entry/title ?v})

(defsub :footer
  [[_ :done-count ?done-count]]
  [[_ :active-count ?active-count]]
  [[_ :visibility-filter ?visibility-filter]]
  =>
  {:active-count ?active-count
   :done-count ?done-count
   :visibility-filter ?visibility-filter})

(rule remove-orphaned-when-unique-conflict
  [[?e ::err/type :unique-conflict]]
  [[?e ::err/failed-insert ?v]]
  [?orphaned <- [(:e ?v) :all]]
  =>
  (retract! ?orphaned))

(session app-session
  'precept.todomvc.rules
  :db-schema precept.todomvc.schema/db-schema
  :client-schema precept.todomvc.schema/client-schema)
