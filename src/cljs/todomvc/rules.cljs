(ns todomvc.rules
  (:require [clara.rules :refer [insert insert-all insert! insert-all!
                                 insert-unconditional!
                                 insert-all-unconditional!
                                 retract! query fire-rules]]
            [todomvc.util :refer [map->tuples
                                  attr-ns
                                  entities-where
                                  entity
                                  qa
                                  clara-tups->maps]]
            [clara.rules.accumulators :as acc]
            [todomvc.tuplerules :refer-macros [def-tuple-session def-tuple-rule def-tuple-query]]))

(defn todo-tx [id title done]
  (merge
    {:db/id      id
     :todo/title title}
    (when-not (nil? done)
      {:todo/done done})))

(defn visibility-filter-tx [id kw]
  {:db/id                id
   :ui/visibility-filter kw})

(defn mark-all-done-action []
  {:db/id              (random-uuid)
   :ui/toggle-complete :tag})

(def clear-completed-action
  {:db/id              (random-uuid)
   :ui/clear-completed :tag})

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
  (insert-unconditional! [?e :todo/done :done]))

(def-tuple-rule remove-toggle-complete-when-all-todos-done
  [?toggle <- :ui/toggle-complete]
  [?total <- (acc/count) :from [:todo/title]]
  [?total-done <- (acc/count) :from [:todo/done]]
  [:test (not (not (= ?total ?total-done)))]
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
  [?action <- [:ui/clear-completed]]
  [:not [:exists [:todo/done]]]
  =>
  (println "Clear-completed action finished. Retracting " ?action)
  (retract! ?action))

;(defrule print-all-facts
;  [?fact <- :all [[e a v]] (= ?e e) (= ?a a) (= ?v v)]
;  =>
;  (println "FACT" ?fact))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Queries
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def-tuple-query find-all-done []
  [[?e ?a ?v]]
  [:test (= (attr-ns ?a) "todo")])

(def-tuple-query find-done-count []
  [?count <- (acc/count) :from [:todo/done]])

(def-tuple-session todos 'todomvc.rules)

;(def facts
;  (apply concat
    ;[[(random-uuid) :today/is-friday :tag]]
    ;(map->tuples (toggle-tx (random-uuid) true))
    ;(map->tuples (visibility-filter-tx (random-uuid) :all))
    ;(mapv map->tuples (repeatedly 5 #(todo-tx (random-uuid) "TODO" nil))))

;(def session (fire-rules (insert-all todos facts)))

;(def all-done (query session find-all-done))


;(cljs.pprint/pprint (entity session (:db/id (first (clara-tups->maps all-done)))))

;(cljs.pprint/pprint  all-done)

;(cljs.pprint/pprint (find-by-attribute session :ui/visibility-filter))

;(cljs.pprint/pprint (map #(entity session (:db/id %)) (qav session :todo/done :done)))
;(cljs.pprint/pprint (entities-where session :todo/visible))

