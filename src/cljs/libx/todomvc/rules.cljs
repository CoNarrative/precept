(ns libx.todomvc.rules
  (:require [clara.rules.accumulators :as acc]
            [libx.spec.sub :as sub]
            [libx.util :refer [insert! insert-unconditional! retract! attr-ns guid]]
            [libx.tuplerules :refer-macros [def-tuple-session def-tuple-rule def-tuple-query]]
            [libx.util :as util]))

(defn log [& args]
  (comment (println args)))

(def-tuple-rule todo-is-visible-when-filter-is-all
  [[_ :ui/visibility-filter :all]]
  [[?e :todo/title]]
  =>
  (insert! [?e :todo/visible :tag]))

(def-tuple-rule todo-is-visile-when-filter-is-done-and-todo-done
  [[_ :ui/visibility-filter :done]]
  [[?e :todo/done]]
  =>
  (insert! [?e :todo/visible :tag]))

(def-tuple-rule todo-is-visible-when-filter-active-and-todo-not-done
  [[_ :ui/visibility-filter :active]]
  [[?e :todo/title]]
  [:not [?e :todo/done]]
  =>
  (log "Active tag found! Marking incomplete todo visible")
  (insert! [?e :todo/visible :tag]))

(def-tuple-rule toggle-all-complete
  [:exists [:ui/toggle-complete]]
  [[?e :todo/title]]
  [:not [?e :todo/done]]
  =>
  (log "Marked done via toggle complete:" ?e)
  (insert-unconditional! [?e :todo/done :tag]))

(def-tuple-rule remove-toggle-complete-when-all-todos-done
  [?toggle <- :ui/toggle-complete]
  [?total <- (acc/count) :from [:todo/title]]
  [?total-done <- (acc/count) :from [:todo/done]]
  [:test (not (not (= ?total ?total-done)))] ;;TODO. '= won't work without not not...
  =>
  (log "Total todos: " ?total)
  (log "Total done: " ?total-done)
  (log "Retracting toggle-all-complete action: " ?toggle)
  (retract! ?toggle))

(def-tuple-rule no-done-todos-when-clear-completed-action
  [:exists [:ui/clear-completed]]
  [[?e :todo/title]]
  [[?e :todo/done]]
  [?entity <- (acc/all) :from [?e :all]]
  =>
  (log "Retracting entity " ?entity)
  (doseq [tuple ?entity] (retract! tuple)))
; DSL notes on how we might handle retractions such as these.
; Follows convention for namespaced keyword destructuring slated for CLJ 1.9
; (retract {:todo/keys :all})
; (retract {:todo/keys [title visible done]})
; (retract-entity ?e)

(def-tuple-rule clear-completed-action-is-done-when-no-done-todos
  [?action <- :ui/clear-completed]
  [:not [:exists [:todo/done]]]
  =>
  (log "Clear-completed action finished. Retracting " ?action)
  (retract! ?action))

(def-tuple-rule find-done-count
  [?done <- (acc/count) :from [:todo/done]]
  [?total <- (acc/count) :from [:todo/title]]
  =>
  (log "done active count" ?done (- ?total ?done))
  (insert! [[(guid) :done-count ?done]
            [(guid) :active-count (- ?total ?done)]]))

(def-tuple-rule subs-footer-controls
  [:exists [?e ::sub/request :footer]]
  [[_ :done-count ?done-count]]
  [[_ :active-count ?active-count]]
  [[_ :ui/visibility-filter ?visibility-filter]]
  =>
  (log "Inserting footer response" ?e)
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
  (log "Inserting visible todo" ?entity)
  (insert! [(guid) :visible-todo ?entity]))

(def-tuple-rule subs-task-list
  [:exists [?e ::sub/request :task-list]]
  [?visible-todos <- (acc/all) :from [:visible-todo]]
  [[_ :active-count ?active-count]]
  =>
  (let [res (map :v ?visible-todos)
        ents (map #(map util/record->vec %) res)
        ms (map util/entity-tuples->entity-map ents)]
    (insert!
      [?e ::sub/response
            {:visible-todos ms
             :all-complete? (= ?active-count 0)}])))

(def-tuple-rule subs-todo-app
  [:exists [?e ::sub/request :todo-app]]
  [?todos <- (acc/all) :from [:todo/title]]
  =>
  ;(log "Inserting all-todos response" (mapv libx.util/record->vec ?todos))
  (insert! [?e ::sub/response "HI"]));(libx.util/tuples->maps (mapv libx.util/record->vec ?todos))]))

(def-tuple-rule subs-new-todo-title
  [:exists [?e ::sub/request :new-todo/title]]
  [[?eid :new-todo/title ?v]]
  =>
  (log "[sub-response] Inserting new-todo-title" ?v)
  (insert! [?e ::sub/response {:db/id ?eid :new-todo/title ?v}]))

;;TODO. Make part of lib
(def-tuple-rule entity-doesnt-exist-when-removal-requested
  [[?e :remove-entity-request ?eid]]
  [?entity <- (acc/all) :from [?eid :all]]
  =>
  (log "Fulfilling remove entity request " ?entity)
  (doseq [tuple ?entity] (retract! tuple)))

(def-tuple-rule save-new-todo-when-press-enter
  [[_ :input/key-code 13]]
  [[?e :new-todo/title]]
  =>
  (log "Inserting save action from RHS" ?e)
  (insert! [?e :new-todo/save :action]))

(def-tuple-rule save-edit-action-when-press-enter
  [[_ :input/key-code 13]]
  [[?e :todo/edit]]
  =>
  (log "Inserting save action from RHS" ?e)
  (insert! [?e :todo/save-edit :action]))

(def-tuple-rule new-todos-become-regular-todos-when-saved
  [[?e :new-todo/save :action]]
  [[?e :new-todo/title ?v]]
  =>
  (log "Saving todo " ?v)
  (insert-unconditional! [?e :todo/title ?v]))

(def-tuple-rule not-a-new-todo-if-regular-todo
  [[?e :todo/title ?v]]
  [?new-todo <- [?e :new-todo/title ?v]]
  =>
  (log "Retracting new-todo" ?new-todo)
  (retract! ?new-todo))

(def-tuple-rule process-edit-request
  [[?e :todo/edit-request :action]]
  [[?e :todo/title ?v]]
  =>
  (log "Responding to edit request" ?e ?v)
  (insert-unconditional! [?e :todo/edit ?v]))

(def-tuple-rule when-save-edit-requested
  [[?e :todo/save-edit :action]]
  [[?e :todo/edit ?v]]
  [?edited <- [?e :todo/title]]
  =>
  (retract! ?edited)
  (insert-unconditional!
    [[?e :todo/title ?v]
     [?e :todo/save-edit-complete :action]]))

(def-tuple-rule when-save-edit-fulfilled
  [[?e :todo/save-edit-complete :action]]
  [?edit <- [?e :todo/edit]]
  =>
  (retract! ?edit))

(def-tuple-rule actions-cleared-at-session-end
  {:salience -100}
  [?action <- [_ :new-todo/save :action]]
  =>
  (log "Removing action" ?action)
  (retract! ?action))

(def-tuple-rule keycode-cleared-at-session-end
  {:salience -100}
  [?fact <- :input/key-code]
  =>
  (log "Removing key-code " ?fact)
  (retract! ?fact))

(def-tuple-session app-session 'libx.todomvc.rules)



;; Problem
;; user types a new-todo
;; one new todo exists in session
;; user presses enter
;; key code 13 is inserted
;; new todo is saved
;; user types a character and new-todo is inserted
;; key-code 13 still exists!
;; new-todo with single character is saved as a new todo
;; key code 13 is replaced (from outside session) by next keycode
;; user can now type a new todo with multiple characters

;; Solution
;; Limit key-code's existence to a single firing
;; by retracting it at the end inside a rule,
