(ns libx.todomvc.rules
  (:require [clara.rules.accumulators :as acc]
            [clara.rules :as cr]
            [libx.core :refer [notify!]]
            [libx.spec.sub :as sub]
            [libx.todomvc.schema :refer [app-schema]]
            [clojure.core.reducers :as r]
            [libx.util :refer [insert! insert-unconditional! retract! attr-ns guid Tuple]]
            [libx.tuplerules :refer-macros [deflogical store-action def-tuple-session def-tuple-rule]]
            [libx.schema :as schema]
            [libx.todomvc.facts :refer [todo]]
            [libx.util :as util]
            [libx.state :as state]))

(defn trace [& args]
  (apply prn args))

(def-tuple-rule all-facts
  [?fact <- [:all]]
  =>
  (println "FACT" (into [] (vals ?fact))))


;; Action handlers
;(store-action :input/key-code-action)
;(store-action :ui/set-visibility-filter-action)
;(store-action :entry/title-action)

(def-tuple-rule handle-set-visibility-filter-action
  {:group :action}
  [[?e :ui/set-visibility-filter-action ?v]]
  =>
  (insert-unconditional!
    [:global :ui/visibility-filter (:ui/visibility-filter ?v)]))

(def-tuple-rule handle-entry-title-action
  {:group :action}
  [[?e :entry/title-action ?v]]
  =>
  (insert-unconditional! [:global :entry/title (:entry/title ?v)]))

(def-tuple-rule handle-start-todo-edit
  {:group :action}
  [[_ :todo/start-edit-action ?action]]
  [[(:id ?action) :todo/title ?v]]
  =>
  (trace "Responding to edit request" (:id ?action) ?v)
  (insert-unconditional! [(:id ?action) :todo/edit ?v]))

(def-tuple-rule handle-update-edit-action
  {:group :action}
  [[_ :todo/update-edit-action ?params]]
  =>
  (insert-unconditional! [(:id ?params) :todo/edit (:value ?params)]))

(def-tuple-rule handle-save-edit-action
  {:group :action}
  [[_ :todo/save-edit-action ?params]]
  [?edit <- [(:id ?params) :todo/edit ?v]]
  =>
  (println "Retracting edit" ?edit)
  (retract! ?edit)
  (insert-unconditional! [(:id ?params) :todo/title ?v]))

(def-tuple-rule handle-toggle-done-action
  {:group :action}
  [[?e :todo/toggle-done-action ?v]]
  =>
  (trace "Responding to toggle done action " [(:id ?v) :todo/done (not (:old-val ?v))])
  (insert-unconditional! [(:id ?v) :todo/done (not (:old-val ?v))]))

(def-tuple-rule handle-clear-completed-action
  {:group :action}
  [[_ :ui/clear-completed-action]]
  [[?e :todo/done true]]
  [?done-entity <- (acc/all) :from [?e :all]]
  =>
  (retract! ?done-entity))

(def-tuple-rule handle-complete-all-action
  {:group :action}
  [[_ :ui/mark-all-done-action]]
  [[?e :todo/done false]]
  =>
  (println "Marking done " ?e)
  (insert-unconditional! [?e :todo/done true]))

;; Calculations
(deflogical [?e :todo/visible :tag] :- [[_ :ui/visibility-filter :all]] [[?e :todo/title]])

(deflogical [?e :todo/visible :tag] :- [[_ :ui/visibility-filter :done]] [[?e :todo/done true]])

(deflogical [?e :todo/visible :tag] :- [[_ :ui/visibility-filter :active]]
                                       [[?e :todo/title]]
                                       [[?e :todo/done false]])

(def-tuple-rule insert-done-count
  [?n <- (acc/count) :from [_ :todo/done true]]
  =>
  (println "Done count : " ?n)
  (insert-unconditional! [:global :done-count ?n]))

(def-tuple-rule insert-active-count
  [[_ :done-count ?done]]
  [?total <- (acc/count) :from [:todo/title]]
  =>
  (println "Active count: " (- ?total ?done))
  (insert-unconditional! [:global :active-count (- ?total ?done)]))


;(deflogical [:global :done-count ?n] :- [?n <- (acc/count) :from [_ :todo/done true]])

;(deflogical [:global :active-count (- ?total ?done)]
;            :- [[_ :done-count ?done]]
;               [?total <- (acc/count) :from [:todo/title]]))

(deflogical [?e :entry/save-action :tag] :- [[_ :input/key-code 13]] [[?e :entry/title]])

(def-tuple-rule handle-entry-save-action
  [[_ :entry/save-action]]
  [?entry <- [_ :entry/title ?v]]
  =>
  (retract! ?entry)
  (insert-unconditional! (todo ?v)))

(deflogical [(guid) :todo/save-edit-action {:id ?e}] :- [[_ :input/key-code 13]] [[?e :todo/edit]])

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

(def-tuple-rule create-list-of-visible-todos
  {:group :report}
  [?eids <- (by-fact-id :e) :from [:todo/visible]]
  [:test (seq ?eids)]
  =>
  (println "List!" ?eids)
  (insert! [(guid) :todos/by-last-modified*order ?eids])
  (doseq [x ?eids]
    (insert! [(guid) :todos/by-last-modified*eid x])))

(def-tuple-rule update-list-of-visible-todos
  {:group :report}
  [[_ :todos/by-last-modified*eid ?e]]
  [?entity <- (acc/all) :from [?e :all]]
  =>
  (println "Entity list!" ?entity)
  (insert! [(guid) :todos/by-last-modified*item ?entity]))

(def-tuple-rule order-list-of-visible-todos
  {:group :report}
  [:exists [?e ::sub/request :task-list]]
  [[_ :todos/by-last-modified*order ?eids]]
  [?items <- (acc/all :v) :from [:todos/by-last-modified*item]]
  [[_ :active-count ?active-count]]
  [:test (seq ?eids)] ;; TODO. Investigate whether us or Clara
  =>
  (let [items (group-by :e (flatten ?items))
        ordered (vals (select-keys items (into [] ?eids)))
        entities (util/entity-Tuples->entity-maps ordered)]
    (println "Entities" entities)
    ; Following are equivalent excepting the second results in order being lost:
    ;(notify! :task-list (fn [x] (if (map? x)
    ;                              (assoc x :visible-todos entities)
    ;                              {:visible-todos entities})
    (insert! [?e ::sub/response {:visible-todos entities
                                 :all-complete? (= 0 ?active-count)}])))

;; Subscription handlers
(def-tuple-rule subs-footer-controls
  {:group :report}
  [:exists [?e ::sub/request :footer]]
  [[_ :done-count ?done-count]]
  [[_ :active-count ?active-count]]
  [[_ :ui/visibility-filter ?visibility-filter]]
  =>
  (trace "Inserting footer response- done active filter" ?done-count ?active-count
    ?visibility-filter)
  (insert!
    [?e ::sub/response
        {:active-count ?active-count
         :done-count ?done-count
         :visibility-filter ?visibility-filter}]))

;(def-tuple-rule acc-todos-that-are-visible
;  [[?e :todo/visible]]
;  [?entity <- (acc/all) :from [?e :all]]
;  =>
;  ;; warning! this is bad!
;  (trace "Inserting visible todo" (mapv vals ?entity))
;  (insert! [(guid) :visible-todo ?entity]))

;(def-tuple-rule subs-task-list
;  [:exists [?e ::sub/request :task-list]]
;  [?visible-todos <- (acc/all) :from [:visible-todo]]
;  [[_ :active-count ?active-count]]
;  =>
;  (let [res (map :v ?visible-todos)
;        ents (map #(map util/record->vec %) res)
;        ms (map util/tuple-entity->hash-map-entity ents)]))
;    ;; FIXME. Ends up overwriting anything via notify! in store. May be problem with add
;    ;; or remove changes method
;    ;(insert!
;    ;  [?e ::sub/response
;    ;        {})]));:visible-todos ms
;             ;:all-complete? (= ?active-count 0)})]))

(def-tuple-rule subs-todo-app
  [:exists [?e ::sub/request :todo-app]]
  [?todos <- (acc/all) :from [:todo/title]]
  =>
  (trace "Inserting all-todos response" (mapv libx.util/record->vec ?todos))
  (insert! [?e ::sub/response "X"]))

(def-tuple-rule subs-task-entry
  [:exists [?e ::sub/request :task-entry]]
  [[?eid :entry/title ?v]]
  =>
  (trace "[sub-response] Inserting new-todo-title" ?v)
  (insert! [?e ::sub/response {:db/id ?eid :entry/title ?v}]))

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
(cr/defrule entity-doesnt-exist-when-removal-requested
  {:group :action}
  [:remove-entity-action (= ?v (:v this))]
  [?entity <- (acc/all) :from [:all (= (:e this) (:id ?v))]]
  =>
  (trace "Fulfilling remove entity request " ?v ?entity)
  (doseq [tuple ?entity]
    (retract! tuple)))

;; TODO. Lib
(def-tuple-rule action-cleanup
  {:group :cleanup}
  [?action <- [_ :action]]
  ;[?actions <- (acc/all) :from [:action]]
  ;[:test (> (count ?actions) 0)]
  =>
  (trace "CLEANING actions" ?action)
  ;(doseq [action ?actions]
  (cr/retract! ?action))


;(def-tuple-rule action-cleanup-2 {:group :cleanup}
;  [?fact <- [:this-tick :all ?v]]
;  =>
;  (trace "CLEANING this-tick fact because transient" ?fact)
;  (cr/retract! ?fact))

(cr/defrule cleanup-transient
   {:group :action}
   [?fact <- :all (= (:e this) :this-tick)]
   =>
   (trace "CLEANING this-tick fact because..." ?fact)
   (cr/retract! ?fact))

(def groups [:action :calc :report :cleanup])
(def activation-group-fn (util/make-activation-group-fn :calc))
(def activation-group-sort-fn (util/make-activation-group-sort-fn groups :calc))
(def hierarchy (schema/schema->hierarchy app-schema))
(def ancestors-fn (util/make-ancestors-fn hierarchy))

;(def-tuple-session app-session
(cr/defsession app-session
  'libx.todomvc.rules
  :fact-type-fn :a
  :ancestors-fn ancestors-fn
  :activation-group-fn activation-group-fn
  :activation-group-sort-fn activation-group-sort-fn)


