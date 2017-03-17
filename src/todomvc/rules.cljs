(ns todomvc.rules
  (:require-macros [clara.macros :refer [defrule defquery defsession]])
  (:require [clara.rules :refer [insert insert-all insert! insert-all!
                                 insert-unconditional!
                                 insert-all-unconditional!
                                 retract! query fire-rules]]
            [clara.rules.accumulators :as acc]))


(defn map->tuple [m]
  (let [eid     (:db/id m)
        temp-id (random-uuid)]
    (conj
      (mapv (fn [[a v]] [eid a v]) (dissoc m :db/id)))))
;[temp-id :db/id eid])))

;(defrecord Todo [id title done])
;
(defn todo-tx [id title done]
  (merge
    {:db/id        id
     :todo/title   title
     :todo/visible false}
    (when-not (nil? done)
      {:todo/status done})))

;(defrecord Showing [key])
;
(defn showing-tx [id kw]
  {:db/id                id
   :ui/visibility-filter kw})

;(defrecord VisibleTodos [todos])
;
;(defrecord ToggleComplete [])
;
(defn toggle-tx [id bool]
  {:db/id              id
   :ui/toggle-complete bool})

;(defrule show-all
;  [Showing (= key :all)]
;  [?todos <- (acc/all) :from [Todo]]
;  =>
;  (prn "show-all rule fired" ?todos)
;  (insert! (->VisibleTodos ?todos)))
;
(defrule show-all
  ; when visibility filter set to all
  [:ui/visibility-filter [[e a v]] (= v :all)]
  ; and a todo is not visible
  [?not-visible <- :todo/visible [[e a v]] (= v false) (= e ?e)]
  =>
  (println "not visible" ?not-visible)
  (retract! ?not-visible)
  (println "inserting" [?e :todo/visible true])
  (insert! [?e :todo/visible true]))

;(defrule show-done
;  [Showing (= key :done)]
;  [?todos <- (acc/all) :from [Todo (= done true)]]
;  =>
;  (prn "show-done rule fired")
;  (insert! (->VisibleTodos ?todos)))
;
(defrule show-done
  ; when visibility filter set to "done"
  [:ui/visibility-filter [[e a v]] (= v :done)]
  ; and a todo is not visible
  [?fact <- :todo/visible [[e a v]] (= v false) (= e ?e)]
  ; and its status is "done"
  [:todo/status [[e a v]] (= e ?e) (= v :done)]
  =>
  (retract! ?fact)
  (insert! [?e :todo/visible true]))

;(defrule show-active
;  [Showing (= key :active)]
;  [?todos <- (acc/all) :from [Todo (= done false)]]
;  =>
;  (prn "show-active rule fired")
;  (insert! (->VisibleTodos ?todos)))
;
(defrule show-active
  ; when visibility filter set to "done"
  [:ui/visibility-filter [[e a v]] (= v :active)]
  ; and a todo is not visible
  [?fact <- :todo/visible [[e a v]] (= v false) (= e ?e)]
  ; and it's not done (has no associated status)
  [:not [:todo/status [[e a v]] (= e ?e)]]
  =>
  (retract! ?fact)
  (insert! [?e :todo/visible true]))

;(defrule toggle-all-complete
;  [?toggle <- ToggleComplete]
;  [?todos <- (acc/all) :from [Todo]]
;  =>
;  (prn "toggle-all-complete rule fired")
;  (prn "inserting" (map #(update % :done not) ?todos))
;  (retract! ?toggle)
;  (apply retract! ?todos)
;  (insert-all-unconditional! (map #(update % :done not) ?todos)))
;
(defrule toggle-all-complete
  ; when toggle complete is true
  [?toggle <- :ui/toggle-complete [[e a v]] (= v true)]
  ; and a todo
  [:todo/title [[e a v]] (= ?e e)]
  ; that isn't done
  [:not [:todo/status [[e a v]] (= e ?e)]]
  =>
  (println "Mark done via toggle complete:" ?e)
  (insert-unconditional! [?e :todo/status :done]))
; and todos/status :done count === num of todos

(defrule remove-toggle
  [?toggle <- :ui/toggle-complete [[e a v]] (= v true)]
  [?total <- (acc/count) :from [:todo/title]]
  [?total-done <- (acc/count) :from [:todo/status]]
  [:test (not (not (= ?total ?total-done)))]
  =>
  (println "Total todos " ?total)
  (println "Total done " ?total-done)
  (println "Retracting toggle: " ?toggle)
  (retract! ?toggle))
;(defquery find-showing
;  []
;  [?showing <- Showing])
;
;(defquery find-visible-todos
;  []
;  [?visible-todos <- VisibleTodos])
;
;(defquery find-todos
;  []
;  [?todos <- (acc/all) :from [Todo]])
;
;(defquery find-todo
;  [:?id])
;  [?todo <- Todo (= id ?id)])
;
(defquery find-todo [:?eid]
  [?todo <- :all [[e a v]] (= e ?eid)])

;; MVCtodo uses sequential ids. Since this is a horrible idea, I'm skipping it this pass.
;(defquery find-max-id
;  []
;  [?id <- (acc/max :id) :from [Todo]])
;
;(defquery find-all-done
;  []
;  [?todos <- (acc/all) :from [Todo (= done true)]])
;
(defquery find-all-done []
  ;[:all [[e a v]] (= e ?e) (= a ?a) (= v ?v)]
  [:all [[e a v]] (= e ?e) (= a ?a) (= v ?v)]
  [:test (= (attr-ns ?a) "todo")])

;(defquery find-done-count
;  []
;  [?count <- (acc/count) :from [Todo (= done true)]])
;
(defquery find-done-count []
  [?count <- (acc/count) :from [:todo/status [[e a v]] (= v :done)]])


(defsession todos 'todomvc.rules
  :fact-type-fn (fn [[e a v]] a)
  :ancestors-fn (fn [type] [:all]))

(def facts
  (apply concat
    (map->tuple (toggle-tx (random-uuid) true))
    (map->tuple (showing-tx (random-uuid) :all))
    (mapv map->tuple (repeatedly 5 #(todo-tx (random-uuid) "TODO" nil)))))


@(def session (fire-rules (insert-all todos facts)))

@(def all-done (query session find-all-done))

(defn clara-tups->maps
  [tups]
  (->> (group-by :?e tups)
    (mapv (fn [[id ent]]
           (into {:db/id id}
             (reduce (fn [m tup] (assoc m  (:?a tup) (:?v tup)))
               {} ent))))))


(defn attr-ns [attr]
  (subs (first (clojure.string/split attr "/")) 1))

(cljs.pprint/pprint (clara-tups->maps all-done))


(println "Done count: " (query session find-done-count))

(println facts)

(map->tuple (showing-tx (random-uuid) :all))
