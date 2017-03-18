(ns todomvc.rules
  (:require-macros [clara.macros :refer [defrule defquery defsession]])
  (:require [clara.rules :refer [insert insert-all insert! insert-all!
                                 insert-unconditional!
                                 insert-all-unconditional!
                                 retract! query fire-rules]]
            [todomvc.util :refer [map->tuple
                                  attr-ns
                                  clara-tups->maps]]
            [clara.rules.accumulators :as acc]
            [todomvc.macros :refer [defn-tuple-session]]))


(defn todo-tx [id title done]
  (merge
    {:db/id        id
     :todo/title   title}
    (when-not (nil? done)
      {:todo/done done})))

(defn visibility-filter-tx [id kw]
  {:db/id                id
   :ui/visibility-filter kw})

(defn toggle-tx [id bool]
  {:db/id              id
   :ui/toggle-complete bool})


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
  ; when toggle complete is true
  [:ui/toggle-complete [[e a v]] (= v true)]
  ; and a todo
  [:todo/title [[e a v]] (= ?e e)]
  ; that isn't done
  [:not [:todo/done [[e a v]] (= e ?e)]]
  =>
  (println "Mark done via toggle complete:" ?e)
  (insert-unconditional! [?e :todo/done :done]))

(defrule remove-toggle
  [?toggle <- :ui/toggle-complete [[e a v]] (= v true)]
  [?total <- (acc/count) :from [:todo/title]]
  [?total-done <- (acc/count) :from [:todo/done]]
  [:test (not (not (= ?total ?total-done)))]
  =>
  (println "Total todos " ?total)
  (println "Total done " ?total-done)
  (println "Retracting toggle: " ?toggle)
  (retract! ?toggle))

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


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Queries
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defquery find-by-attribute-
  [:?a]
  [:all [[e a v]] (= e ?e) (= a ?a) (= v ?v)])

(defn find-by-attribute [session kw]
  (clara-tups->maps
    (query session find-by-attribute- :?a kw)))

(defn entities-where
  "Returns hydrated entities matching an attribute-only or an attribute-value query"
  ([session a] (map #(entity session (:db/id %)) (find-by-attribute session a)))
  ([session a v] (map #(entity session (:db/id %)) (qav session a v)))
  ([session a v e] (map #(entity session (:db/id %)) (qave session a v e))))

(defquery find-all-done []
  [:all [[e a v]] (= e ?e) (= a ?a) (= v ?v)]
  [:test (= (attr-ns ?a) "todo")])

(defquery find-done-count []
  [?count <- (acc/count) :from [:todo/done [[e a v]] (= v :done)]])

(defsession todos 'todomvc.rules
  :fact-type-fn (fn [[e a v]] a)
  :ancestors-fn (fn [type] [:all]))

(def facts
  (apply concat
    ;[[(random-uuid) :today/is-friday :tag]]
    (map->tuple (toggle-tx (random-uuid) true))
    (map->tuple (visibility-filter-tx (random-uuid) :all))
    (mapv map->tuple (repeatedly 5 #(todo-tx (random-uuid) "TODO" nil)))))


(def session (fire-rules (insert-all todos facts)))

(def all-done (query session find-all-done))


(cljs.pprint/pprint (entity session (:db/id (first (clara-tups->maps all-done)))))

(cljs.pprint/pprint  all-done)

(cljs.pprint/pprint (find-by-attribute session :ui/visibility-filter))

;(cljs.pprint/pprint (map #(entity session (:db/id %)) (qav session :todo/done :done)))
(cljs.pprint/pprint (entities-where session :todo/visible))

