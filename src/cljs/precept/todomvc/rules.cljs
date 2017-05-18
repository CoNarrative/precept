(ns precept.todomvc.rules
  (:require-macros [precept.dsl :refer [<- entity]])
  (:require [clara.rules.accumulators :as acc]
            [clara.rules :as cr]
            [precept.spec.sub :as sub]
            [precept.todomvc.schema :refer [app-schema]]
            [precept.util :refer [insert! insert-unconditional! retract! guid] :as util]
            [precept.tuplerules :refer-macros [deflogical
                                               defsub
                                               def-tuple-session
                                               def-tuple-rule]]
            [precept.schema :as schema]
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

;; Calculations
(deflogical [?e :todo/visible :tag] :- [[_ :ui/visibility-filter :all]]
                                       [[?e :todo/title]])

(deflogical [?e :todo/visible :tag] :- [[_ :ui/visibility-filter :done]]
                                       [[?e :todo/done true]])

(deflogical [?e :todo/visible :tag] :- [[_ :ui/visibility-filter :active]]
                                       [[?e :todo/done false]])

(deflogical [?e :entry/save-action :tag] :- [[_ :input/key-code 13]]
                                            [[?e :entry/title]])

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

(defn by-fact-id
  ([]
   (acc/accum
     {:initial-value []
      :reduce-fn (fn [acc cur] (sort-by :t (conj acc cur)))
      :retract-fn (fn [acc cur] (sort-by :t (remove #(= cur %) acc)))}))
  ([k]
   (acc/accum
     {:initial-value []
      :reduce-fn (fn [acc cur]
                   (trace "[by-fact-id] reduce fn acc cur" acc cur)
                   (sort-by :t (conj acc (k cur))))
      :retract-fn (fn [acc cur]
                    (trace "[by-fact-id] retract fn acc cur" acc cur)
                    (trace "[by-fact-id] returning " (sort-by :t (remove #(= (k cur) %) acc)))
                    (sort-by :t (remove #(= (k cur) %) acc)))})))

;; TODO. Would like to condense since all this does it create/maintain a list
(def-tuple-rule create-list-of-visible-todos
  {:group :report}
  [?eids <- (by-fact-id :e) :from [:todo/visible]]
  [:test (seq ?eids)]
  =>
  (trace "List!" ?eids)
  (insert! [(guid) :todos/by-last-modified*order ?eids])
  (doseq [x ?eids]
    (insert! [(guid) :todos/by-last-modified*eid x])))

(def-tuple-rule update-list-of-visible-todos
  {:group :report}
  [[_ :todos/by-last-modified*eid ?e]]
  [(<- ?entity (entity ?e))]
  =>
  (trace "Entity list!" ?entity)
  (insert! [(guid) :todos/by-last-modified*item ?entity]))

;; Subscription handlers
;; TODO. Because we want to eliminate subscriptions we should invest minimal effort here.
;; Would like the following for a list sub:
;'(defsub :task-list
;   [[_ :visibile-todos-list ?visible-todos]]
;   [[_ :active-count ?active-count]]
;   =>
;   {:visible-todos ?visible-todos
;    :all-complete? (= ?active-count 0)})

(defsub :task-list
  [[_ :todos/by-last-modified*order ?eids]]
  [?items <- (acc/all :v) :from [:todos/by-last-modified*item]]
  [[_ :active-count ?active-count]]
  [:test (seq ?eids)]
  =>
  (let [items (group-by :e (flatten ?items))
        ordered (vals (select-keys items (into [] ?eids)))
        entities (util/entity-Tuples->entity-maps ordered)]
    (trace "Entities" entities)
    {:visible-todos entities
     :all-complete? (= 0 ?active-count)}))

(defsub :task-entry
  [[?e :entry/title ?v]]
  =>
  {:db/id ?e :entry/title ?v})

(defsub :footer
  [[_ :done-count ?done-count]]
  [[_ :active-count ?active-count]]
  [[_ :ui/visibility-filter ?visibility-filter]]
  =>
  {:active-count ?active-count
   :done-count ?done-count
   :visibility-filter ?visibility-filter})

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

(def-tuple-session app-session 'precept.todomvc.rules :schema app-schema)
