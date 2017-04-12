(ns libx.todomvc.rules
  (:require [clara.rules :refer [insert! insert-all! insert-unconditional! retract!]]
            [clara.rules.accumulators :as acc]
            [libx.util :refer [attr-ns]]
            [libx.tuplerules :refer-macros [def-tuple-session def-tuple-rule def-tuple-query]]))


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
  (insert! [?e :todo/visible :tag]))

(def-tuple-rule toggle-all-complete
  [:exists [:ui/toggle-complete]]
  [[?e :todo/title]]
  [:not [?e :todo/done]]
  =>
  (println "Marked done via toggle complete:" ?e)
  (insert-unconditional! [?e :todo/done :tag]))

(def-tuple-rule remove-toggle-complete-when-all-todos-done
  [?toggle <- :ui/toggle-complete]
  [?total <- (acc/count) :from [:todo/title]]
  [?total-done <- (acc/count) :from [:todo/done]]
  [:test (= ?total ?total-done)]
  =>
  (println "Total todos: " ?total)
  (println "Total done: " ?total-done)
  (println "Retracting toggle-all-complete action: " ?toggle)
  (retract! ?toggle))

(def-tuple-rule no-done-todos-when-clear-completed-action
  [:exists [:ui/clear-completed]]
  [[?e :todo/title]]
  [[?e :todo/done]]
  [?entity <- (acc/all) :from [?e :all]]
  =>
  (println "Retracting entity " ?entity)
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
  (println "Clear-completed action finished. Retracting " ?action)
  (retract! ?action))

(def-tuple-rule print-all-facts
  [?fact <- [?e ?a ?v]]
  =>
  (println "FACT" ?fact))

(def-tuple-rule find-done-count
  [?done <- (acc/count) :from [:todo/done]]
  [?total <- (acc/count) :from [:todo/title]]
  =>
  (println "done active count" ?done (- ?total ?done))
  (insert-all! [[-1 :done-count ?done]
                [-1 :active-count (- ?total ?done)]]))

(def-tuple-rule subs-footer-controls
  [[?e :component/footer]]
  [?done-count <- [:done-count]]
  [?active-count <- [:active-count]]
  =>
  (insert-all! [[?e :footer {:active-count ?active-count
                             :done-count ?done-count}]]))

(def-tuple-rule subs-task-list
  [[?e :component/task-list]]
  [?visible-todos <- (acc/all) :from [:todo/visible]]
  [?active-count <- [:active-count]]
  =>
  (insert-all! [[?e :task-list {:visible-todos ?visible-todos
                                    :all-complete? (> ?active-count 0)}]]))
(def-tuple-rule subs-todo-app
  [[?e :component/todo-app]]
  [?todos <- (acc/all) :from [(attr-ns "todo")]]
  =>
  (insert! [?e :todo-app (libx.util/tuples->maps ?todos)]))

(def-tuple-session app-session 'libx.todomvc.rules)
