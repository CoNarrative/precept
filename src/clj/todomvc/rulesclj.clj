(ns todomvc.rulesclj
    (:require [clara.rules :refer [insert insert-all insert! insert-all!
                                   insert-unconditional!
                                   insert-all-unconditional!
                                   retract! query fire-rules
                                   mk-session
                                   defrule defquery defsession]]
              [todomvc.util :refer [map->tuples
                                    attr-ns]]
              [clara.rules.accumulators :as acc]
              [clara.tools.inspect :as inspect]
              [clara.tools.tracing :as trace]
              [clara.rules.compiler :as com]))
[clara.tools.watch :as watch]
[todomvc.macros :refer [def-tuple-session]]


(defn random-uuid []
  (java.util.UUID/randomUUID))

(defn todo-tx [id title done]
  (merge
    {:db/id      id
     :todo/title title}
    (when-not (nil? done)
      {:todo/done done})))

(defn visibility-filter-tx [id kw]
  {:db/id                id
   :ui/visibility-filter kw})

(defn toggle-tx [id bool]
  {:db/id              id
   :ui/toggle-complete bool})

(def clear-completed-action
  {:db/id              (random-uuid)
   :ui/clear-completed :tag})


(defrule todo-is-visible-when-filter-is-all
  [:ui/visibility-filter [[e a v]] (= v :all)]
  [:todo/title [[e a v]] (= ?e e)]
  =>
  (insert! [?e :todo/visible :tag]))

(defrule todo-is-visible-when-filter-is-done-and-todo-done
  [:ui/visibility-filter [[e a v]] (= v :done)]
  [:todo/done [[e a v]] (= e ?e)]
  =>
  (insert! [?e :todo/visible :tag]))

(defrule todo-is-visible-when-filter-active-and-todo-not-done
  [:ui/visibility-filter [[e a v]] (= v :active)]
  [:todo/title [[e a v]] (= ?e e)]
  [:not [:todo/done [[e a v]] (= e ?e)]]
  =>
  (insert! [?e :todo/visible :tag]))

(defrule toggle-all-complete
  ; when toggle complete action exists
  [:exists [:ui/toggle-complete]]
  ; and there's a todo that isn't marked "done"
  [:todo/title [[e a v]] (= ?e e)]
  [:not [:todo/done [[e a v]] (= ?e e)]]
  =>
  (println "Marked done via toggle complete:" ?e)
  (insert-unconditional! [?e :todo/done :done]))

(defrule remove-toggle-complete-when-all-todos-done
  [?toggle <- :ui/toggle-complete [[e a v]] (= v true)]
  [?total <- (acc/count) :from [:todo/title]]
  [?total-done <- (acc/count) :from [:todo/done]]
  [:test (not (not (= ?total ?total-done)))]
  =>
  (println "Total todos: " ?total)
  (println "Total done: " ?total-done)
  (println "Retracting toggle-all-complete action: " ?toggle)
  (retract! ?toggle))

(defrule no-done-todos-when-clear-completed-action
  ; when clear completed action exists
  [:exists [:ui/clear-completed]]
  ; and there's a todo that is marked "done"
  [:todo/title [[e a v]] (= ?e e)]
  [:todo/done [[e a v]] (= ?e e)]
  [?entity <- (acc/all) :from [:all [[e a v]] (= ?e e)]]
  =>
  ; (retract {:todo/keys :all})
  ; (retract {:todo/keys [title visible done]})
  ; (retract-entity ?e)
  ;; can we run a query in RHS???
  (println "Retracting entity " ?entity)
  (doseq [tuple ?entity] (retract! tuple)))

(defrule clear-completed-action-is-done-when-no-done-todos
  [?action <- :ui/clear-completed]
  [:not [:exists [:todo/done]]]
  =>
  (println "Clear-completed action finished. Retracting " ?action)
  (retract! ?action))

;(defrule todo-is-visible-when-a-friday
;  [:exists [:today/is-friday]]
;  [:todo/title [[e a v]] (= ?e e)]
;  =>
;  (println "BOOM")
;  (insert! [?e :todo/visible* :tag]))
;
;(defrule todo-visible-*
;  [:exists [:todo/visible* [[e a v]] (= e? e)]]
;  =>
;  (insert! [?e :todo/visible :tag]))

(defrule print-all-facts
  [?fact <- :all [[e a v]] (= ?e e) (= ?a a) (= ?v v)]
  =>
  (println "FACT" ?fact))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Queries
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;(defquery find-all-done []
;  [:all [[e a v]] (= e ?e) (= a ?a) (= v ?v)]
;  [:test (= (attr-ns ?a) "todo")])
;
;(defquery find-done-count []
;  [?count <- (acc/count) :from [:todo/done]])

;(defsession todos 'todomvc.rulesclj
;  :fact-type-fn (fn [[e a v]] a)
;  :ancestors-fn (fn [type] [:all]))

(def empty-session
  (mk-session 'todomvc.rulesclj
    :fact-type-fn (fn [x] (second x))
    :ancestors-fn (fn [type] [:all])))

(def facts
  (apply concat
    (map->tuples (toggle-tx (random-uuid) true))
    (map->tuples (visibility-filter-tx (random-uuid) :active))
    (mapv map->tuples (repeatedly 5 #(todo-tx (random-uuid) 1111 nil)))))

(println "facts" facts)

(def wfacts (fire-rules (insert-all empty-session facts)))

(-> [wfacts] clara.rules.compiler/to-beta-graph clojure.pprint/pprint)
(-> [wfacts] clara.rules.compiler/to-alpha-graph clojure.pprint/pprint)
(inspect/inspect wfacts)

(defrecord Entity [e a v])

(defrule myrule
  [Entity (= ?v v) (= ?e e)]
  [Entity (= a ?v) (= ?e2 e)]
  [:test (= ?e2 ?e)]
  =>
  (println "Found Entity whose value is the same as one of its attributes!"))

(defrule mytuplerule
  [:all [[e a v]] (= ?v v) (= ?e e)]
  [:all [[e a v]] (= a ?v) (= ?e2 e)]
  [:test (= ?e2 ?e)]
  =>
  (println "Found :entity whose value is the same as one of its attributes!"))

(->Entity 1 2 3)

(def record-session
  (->
    (mk-session [myrule])
    (trace/with-tracing)
    (insert-all (into
                    [(->Entity 123 :hi "there")
                     (->Entity 123 :oh :hi)]
                   (repeatedly 5 #(->Entity "null" true "null"))))
    (fire-rules)))

(def tuple-session
  (->
    (mk-session [mytuplerule]
      :fact-type-fn (fn [[e a v]] a)
      :ancestors-fn (fn [type] [:all]))
    (trace/with-tracing)
    (insert-all (into
                    [[123 :hi "there"]
                     [123 :oh :hi]]
                 (repeatedly 5 #(vector "null" true "null"))))
    (fire-rules)))
(-> [record-session] clara.rules.compiler/to-beta-graph clojure.pprint/pprint)
(-> [record-session] clara.rules.compiler/to-alpha-graph clojure.pprint/pprint)
(inspect/inspect record-session)
(inspect/explain-activations record-session)
(trace/get-trace record-session)

(-> [record-session] com/to-beta-graph clojure.pprint/pprint)

(-> [tuple-session] clara.rules.compiler/to-beta-graph clojure.pprint/pprint)
;(-> [tuple-session] clara.rules.compiler/to-alpha-tree clojure.pprint/pprint)
(inspect/inspect tuple-session)
(inspect/explain-activations tuple-session)
(trace/get-trace tuple-session)
