(ns precept.todomvc.rules
  (:require-macros [precept.dsl :refer [<- entity entities]])
  (:require [precept.accumulators :as acc]
            [precept.spec.error :as err]
            [precept.util :refer [insert! insert-unconditional! retract! guid] :as util]
            [precept.tuplerules :refer-macros [deflogical defsub def-tuple-session def-tuple-rule]]
            [precept.todomvc.facts :refer [todo entry done-count active-count visibility-filter]]))

(defn trace [& args]
  (apply prn args))

(def-tuple-rule all-facts
  [?fact <- [:all]]
  =>
  (trace "FACT" (into [] (vals ?fact))))

(def-tuple-rule handle-save-edit-transient
  {:group :action}
  [[_ :todo/save-edit ?e]]
  [?edit <- [?e :todo/edit ?v]]
  =>
  (trace "Retracting edit" ?edit)
  (retract! ?edit)
  (insert-unconditional! [?e :todo/title ?v]))

(def-tuple-rule handle-clear-completed-transient
  {:group :action}
  [[_ :clear-completed]]
  [[?e :todo/done true]]
  [(<- ?done-entity (entity ?e))]
  =>
  (retract! ?done-entity))

(def-tuple-rule handle-complete-all-transient
  {:group :action}
  [[_ :mark-all-done]]
  [[?e :todo/done false]]
  =>
  (trace "Marking done " ?e)
  (insert-unconditional! [?e :todo/done true]))

(def-tuple-rule create-todo
  {:group :action}
  [[_ :todo/create]]
  [?entry <- [_ :entry/title ?v]]
  =>
  (trace "Creating new todo " ?v)
  (retract! ?entry)
  (insert-unconditional! (todo ?v)))

(def-tuple-rule todo-is-visible-when
  {:group :calc}
  [:or [:and [_ :visibility-filter :all] [?e :todo/title]]
       [:and [_ :visibility-filter :done] [?e :todo/done true]]
       [:and [_ :visibility-filter :active] [?e :todo/done false]]]
  =>
  (println "inserting visible todo fact")
  (insert! [?e :todo/visible :tag]))

;; Calculations
;(deflogical [?e :todo/visible :tag] :- [[_ :visibility-filter :all]]
;                                       [[?e :todo/title]]]))
;
;(deflogical [?e :todo/visible :tag] :- [[_ :visibility-filter :done]]
;                                       [[?e :todo/done true]]]))
;
;(deflogical [?e :todo/visible :tag] :- [[_ :visibility-filter :active]]
;                                       [[?e :todo/done false]]]))
;
;(deflogical [?e :entry/save-action :tag] :- [[_ :input/key-code 13]]
;                                            [[?e :entry/title]]]))

;; TODO. These work and should not. Rules that match their consequences are in :action group
;; which precedes :calc group
(deflogical [:transient :todo/save-edit ?e] :- [[_ :input/key-code 13]]
                                               [[?e :todo/edit]])

(deflogical [:transient :todo/create :tag] :- [[_ :input/key-code 13]]
                                              [[_ :entry/title]])

(def-tuple-rule insert-done-count
  [?n <- (acc/count) :from [_ :todo/done true]]
  =>
  (trace "Done count : " ?n)
  (insert-unconditional! (done-count ?n)))

(def-tuple-rule insert-active-count
  [[_ :done-count ?done]]
  [?total <- (acc/count) :from [:todo/title]]
  =>
  (trace "Active count: " (- ?total ?done))
  (insert-unconditional! (active-count (- ?total ?done))))

;; Subscription handlers
(defsub :task-list
  [?eids <- (acc/by-fact-id :e) :from [:todo/visible]]
  [(<- ?visible-todos (entities ?eids))]
  [[_ :active-count ?active-count]]
  =>
  (let [_ (println "Visible todos" ?visible-todos)
        _ (println "Accum eids!" ?eids)]
    {:visible-todos ?visible-todos
     :all-complete? (= 0 ?active-count)}))

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

;; Error handling
(def-tuple-rule remove-orphaned-when-unique-conflict
  [[?e ::err/type :unique-conflict]]
  [[?e ::err/failed-insert ?v]]
  [?orphaned <- [(:e ?v) :all]]
  =>
  (trace "[error] :unique-conflict. Removing orphaned fact" ?orphaned)
  (retract! ?orphaned))

;;TODO. Lib?
;; TODO. Investigate why sexpr in first position (:id ?v) fails
;(def-tuple-rule entity-doesnt-exist-when-removal-requested
;  {:group :action}
;  [[_ :remove-entity-action ?v]]
;  [?entity <- (acc/all) :from [(:id ?v) :all]]
;  =>
;  (trace "Fulfilling remove entity request " ?v ?entity)
;  (doseq [tuple ?entity]
;    (retract! tuple)))
(def-tuple-rule remove-entity-transient
  {:group :action}
  [[_ :remove-entity ?e]]
  [(<- ?entity (entity ?e))]
  =>
  (trace "Fulfilling remove entity request " ?entity)
  (doseq [tuple ?entity]
    (retract! tuple)))

(def-tuple-session app-session 'precept.todomvc.rules :schema precept.todomvc.schema/app-schema)
