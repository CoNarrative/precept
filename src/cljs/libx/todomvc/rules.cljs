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

;; Action handlers
;(store-action :input/key-code-action)
;(store-action :ui/set-visibility-filter-action)

;; Until we can group store-action/as-insert into :action
(def-tuple-rule handle-entry-update
  {:group :action}
  [[?e :entry/update-action ?v]]
  =>
  (trace "Responding to edit request" ?e ?v)
  (insert-unconditional! [?e :entry/title (:value ?v)]))

(def-tuple-rule handle-key-code-action
  {:group :action}
  [[_ :input/key-code-action ?v]]
  =>
  (insert-unconditional!
    (util/gen-Tuples-from-map ?v)))

(def-tuple-rule handle-set-visibility-filter-action
  {:group :action}
  [[_ :ui/set-visibility-filter-action ?v]]
  =>
  (insert-unconditional! (util/gen-Tuples-from-map ?v)))

(def-tuple-rule handle-start-todo-edit
  [[?e :todo/start-edit-action]]
  [[?e :todo/title ?v]]
  =>
  (trace "Responding to edit request" ?e ?v)
  (insert-unconditional! [?e :todo/edit ?v]))

;; Calculations

;; FIXME. One of these throwing error that is hard/impossible to trace. See if related to registry
;(deflogical [?e :todo/visible :tag] :- [[_ :ui/visibility-filter :all]], [[?e :todo/title]])

;(deflogical [?e :todo/visible :tag] :- [[_ :ui/visibility-filter :done]] [[?e :todo/done]])

;(deflogical [?e :todo/visible :tag] :- [[_ :ui/visibility-filter :active]]
;                                       [[?e :todo/title]]
;                                       [:not [?e :todo/done]]]))

(def-tuple-rule active-done-count
  [?done <- (acc/count) :from [:todo/done]]
  [?total <- (acc/count) :from [:todo/title]]
  =>
  (insert! [[(guid) :done-count ?done]
            [(guid) :active-count (- ?total ?done)]]))

;(deflogical [[(guid) :done-count ?done]
;             [(guid) :active-count (- ?total ?done)]]
;            :- [?done <- (acc/count) :from [:todo/done]]
;               [?total <- (acc/count) :from [:todo/title]])

(deflogical [?e :entry/save-action] :- [[_ :input/key-code 13]] [[?e :entry/title]])

(deflogical [?e :todo/save-edit-action] :- [[_ :input/key-code 13]] [[?e :todo/edit]])

(def-tuple-rule all-facts
  [?fact <- [:all]]
  =>
  (println "FACT" ?fact))


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
  (trace "Inserting visible todo" ?entity)
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


;; Cleanup phase
;; TODO. Lib
;(def-tuple-rule actions-cleared-at-session-end
;  {:salience -100}
;  [?action <- [_ :new-todo/save :action]]
;  =>
;  (trace "Removing action" ?action)
;  (retract! ?action))

;; TODO. Lib / schema
;(def-tuple-rule keycode-cleared-at-session-end
;  {:salience -100}
;  [?fact <- :input/key-code]
;  =>
;  (trace "Removing key-code " ?fact)
;  (retract! ?fact))

;;TODO. Lib
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
  [?actions <- (acc/all) :from [:action]]
  [:test (> (count ?actions) 0)]
  =>
  (trace "CLEANING actions" ?actions)
  (doseq [action ?actions]
    (cr/retract! action)))

(def groups [:action :calc :report :cleanup])
(def activation-group-fn (util/make-activation-group-fn :calc))
(def activation-group-sort-fn (util/make-activation-group-sort-fn groups :calc))
(def hierarchy (schema/schema->hierarchy app-schema))
(def ancestors-fn (util/make-ancestors-fn hierarchy))

(def-tuple-session app-session
  'libx.todomvc.rules
  :ancestors-fn ancestors-fn
  :activation-group-fn activation-group-fn
  :activation-group-sort-fn activation-group-sort-fn)
