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
  "Custsom accumulator.

  Like acc/all ewxcept sorts tuples by :t slot (fact-id). Since fact ids are created sequentially
  this orders facts by they were created.
  Returns list of facts. Optional `k` arg maps `k` over facts."
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

(defn list-of
  "Custom accumulator.
  Calls fact-f on facts being accumulated.
  If provided, calls list-f on accumulated list result."
  ([fact-f]
   (acc/accum
     {:initial-value []
      :reduce-fn (fn [acc cur] (fact-f (conj acc cur)))
      :retract-fn (fn [acc cur] (fact-f (remove #(= cur %) acc)))}))
  ([fact-f list-f]
   (acc/accum
     {:initial-value []
      :reduce-fn (fn [acc cur]
                   (trace "[mk-list] reduce fn acc cur" acc cur)
                   (list-f (conj acc (fact-f cur))))
      :retract-fn (fn [acc cur]
                    (trace "[mk-list] retract fn acc cur" acc cur)
                    (trace "[mk-list] returning " (list-f (remove #(= (fact-f cur) %) acc)))
                    (list-f (remove #(= (fact-f cur) %) acc)))})))

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
;; Determining required rule structure, whether we need multiple or can expand inline per normal
;; ...looks like we need to support rules that generate rules

;;; Impl:
;
;;; `entities` should expand to:
;(rule impl-a
;  [[?req ::gen-fact/request :entities]]
;  [[?req :entities/eid ?e]]
;  [[(<- ?entity (entity ?e))]]
; =>
; (insert! [?req :entities/entity ?e]))
;
;(rule impl-b
;  [[?req :entities/order ?order]]
;  [?ents <- (acc/all :v) :from [?req :entities/entity]]
; =>
; (insert! [?req :entities/list ?e]))
;
;;; Usage:
;(rule my-rule
;  [?eids <- (acc/all :e) :from [:interesting-fact]]
;  [(<- ?interesting-entities (entities ?eids))]
;  =>
;  ;; Prints list of Tuples
;  (println "Found entities with interesting fact" ?interesting-entities))
;
;;; Should expand to:
;(rule my-rule___split-0
;  [?eids <- (acc/all :e) :from [:interesting-fact]
;   =>
;   (let [req-id (guid)]
;     (insert! [req-id ::gen-fact/request :entities])
;     (doseq [eid ?eids] (insert! [req-id :entities/eid ?eid])))])
;
;(rule my-rule
;  ;; ...rest LHS
;   [[req-id ::gen-fact/response ?interesting-entities]]
;   =>
;   ;; ...rest RHS
;   (println "Found entities with interesting fact" ?interesting-entities))
;


[?eids <- (list-of :e #(sort-by :t %)) :from [:todo/visible]]
[?eids <- (by-fact-id :e) :from [:todo/visible]]
[[_ :todos/by-last-modified*eid ?e]]
[(<- ?entity (entity ?e))]


'[(mk-list :todos/by-last-modified (by-fact-id :e) :from [:todo/visible])]
'[(<- ?ordered-visible-todos (entities (by-fact-id :e) :from [:todo/visible]))]
'[(<- ?ordered-visible-todos (order :asc (entities [:todo/visible])))]

;; Subscription handlers
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
