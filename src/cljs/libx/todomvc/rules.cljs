(ns libx.todomvc.rules
  (:require [clara.rules.accumulators :as acc]
            [clara.rules :as cr]
            [libx.spec.sub :as sub]
            [libx.todomvc.schema :refer [app-schema]]
            [libx.util :refer [insert! insert-unconditional! retract! attr-ns guid Tuple]]
            [libx.tuplerules :refer-macros [deflogical store-action def-tuple-session def-tuple-rule]]
            [libx.schema :as schema]
            [libx.util :as util]))

(defn trace [& args]
  (apply prn args))

(def-tuple-rule all-facts
  [?fact <- [:all]]
  =>
  (println "FACT" (into [] (vals ?fact))))


;; Action handlers
(store-action :input/key-code-action)
(store-action :ui/set-visibility-filter-action)
(store-action :entry/title-action)


(def-tuple-rule handle-start-todo-edit
  {:group :action}
  [[?e :todo/start-edit-action]]
  [[?e :todo/title ?v]]
  =>
  (trace "Responding to edit request" ?e ?v)
  (insert-unconditional! [?e :todo/edit ?v]))

(def-tuple-rule handle-toggle-done-action
  {:group :action}
  [[?e :todo/toggle-done-action ?v]]
  [[(:id ?v) :todo/done ?bool]]
  =>
  (trace "Responding to toggle done action " ?v)
  (insert! [(:id ?v) :todo/done (not ?bool)]))


;; Calculations
(deflogical [?e :todo/visible :tag] :- [[_ :ui/visibility-filter :all]] [[?e :todo/title]])

(deflogical [?e :todo/visible :tag] :- [[_ :ui/visibility-filter :done]] [[?e :todo/done true]])

(deflogical [?e :todo/visible :tag] :- [[_ :ui/visibility-filter :active]]
                                       [[?e :todo/title]]
                                       [[?e :todo/done false]])

(deflogical [(guid) :done-count ?n] :- [?n <- (acc/count) :from [_ :todo/done true]])

(deflogical [(guid) :active-count (- ?total ?done)]
            :- [?total <- (acc/count) :from [:todo/title]] [[_ :done-count ?done]])

(deflogical [?e :entry/save-action] :- [[_ :input/key-code 13]] [[?e :entry/title]])

(deflogical [?e :todo/save-edit-action] :- [[_ :input/key-code 13]] [[?e :todo/edit]])

;; Subscription handlers
(def-tuple-rule subs-footer-controls
  [:exists [?e ::sub/request :footer]]
  [[_ :done-count ?done-count]]
  [[_ :active-count ?active-count]]
  [[_ :ui/visibility-filter ?visibility-filter]]
  =>
  (trace "Inserting footer response" ?e)
  (insert!
    [?e ::sub/response
        {:active-count ?active-count
         :done-count ?done-count
         :visibility-filter ?visibility-filter}]))

(def-tuple-rule acc-todos-that-are-visible
  [[?e :todo/visible]]
  [?entity <- (acc/all) :from [?e :all]]
  =>
  ;; warning! this is bad!
  (trace "Inserting visible todo" (mapv vals ?entity))
  (insert! [(guid) :visible-todo ?entity]))

(def-tuple-rule subs-task-list
  [:exists [?e ::sub/request :task-list]]
  [?visible-todos <- (acc/all) :from [:visible-todo]]
  [[_ :active-count ?active-count]]
  =>
  (let [res (map :v ?visible-todos)
        ents (map #(map util/record->vec %) res)
        ms (map util/tuple-entity->hash-map-entity ents)]
    (insert!
      [?e ::sub/response
            {:visible-todos ms
             :all-complete? (= ?active-count 0)}])))

(def-tuple-rule subs-todo-app
  [:exists [?e ::sub/request :todo-app]]
  [?todos <- (acc/all) :from [:todo/title]]
  =>
  (trace "Inserting all-todos response" (mapv libx.util/record->vec ?todos))
  (insert! [?e ::sub/response "HI"]));(libx.util/tuples->maps (mapv libx.util/record->vec ?todos))]))

(def-tuple-rule subs-task-entry
  [:exists [?e ::sub/request :task-entry]]
  [[?eid :entry/title ?v]]
  =>
  (trace "[sub-response] Inserting new-todo-title" ?v)
  (insert! [?e ::sub/response {:db/id ?eid :entry/title ?v}]))

;;TODO. Lib?
(def-tuple-rule entity-doesnt-exist-when-removal-requested
  [[_ :remove-entity-request ?eid]]
  [?entity <- (acc/all) :from [?eid :all]]
  =>
  (trace "Fulfilling remove entity request " ?entity)
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

(cr/defrule remove-older-unique-identity-facts
  {:super true :salience 100}
  [:unique-identity (= ?a1 (:a this)) (= ?t1 (:t this))]
  [?fact2 <- :unique-identity (= ?a1 (:a this)) (= ?t2 (:t this))]
  [:test (> ?t1 ?t2)]
  =>
  (trace (str "SCHEMA MAINT - :unique-identity" ?t1 " is greater than " ?t2))
  (retract! ?fact2))

(cr/defrule remove-older-unique-value-facts
  {:super true :salience 100}
  [?fact1 <- :unique-value (= ?e1 (:e this)) (= ?a1 (:a this)) (= ?t1 (:t this))]
  [?fact2 <- :unique-value (= ?e1 (:e this)) (= ?a1 (:a this)) (= ?t2 (:t this))]
  [:test (> ?t1 ?t2)]
  =>
  (trace (str "SCHEMA MAINT - :unique-value : " ?t1 " is greater than " ?t2))
  (retract! ?fact2))

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


