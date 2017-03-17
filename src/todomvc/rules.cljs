(ns todomvc.rules
  (:require-macros [clara.macros :refer [defrule defquery defsession]])
  (:require [clara.rules :refer [insert insert-all insert! insert-all!
                                 insert-unconditional!
                                 insert-all-unconditional!
                                 retract! query fire-rules]]
            [clara.rules.accumulators :as acc]))


(defn attr-ns [attr]
  (subs (first (clojure.string/split attr "/")) 1))

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
     :todo/title   title}

    (when-not (nil? done)
      {:todo/done done})))

;(defrecord Showing [key])
;
(defn visibility-filter-tx [id kw]
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
(defrule todo-is-visible-when-filter-is-all
  [:ui/visibility-filter [[e a v]] (= v :all)]
  [:todo/title [[e a v]] (= ?e e)]
  =>
  (insert! [?e :todo/visible :tag]))

;(defrule show-done
;  [Showing (= key :done)]
;  [?todos <- (acc/all) :from [Todo (= done true)]]
;  =>
;  (prn "show-done rule fired")
;  (insert! (->VisibleTodos ?todos)))
;
(defrule todo-is-visible-when-filter-is-done-and-todo-done
  [:ui/visibility-filter [[e a v]] (= v :done)]
  [:todo/done [[e a v]] (= e ?e)]
  =>
  (insert! [?e :todo/visible :tag]))

(defrule todo-is-visible-when-a-friday
  [:exists [:today/is-friday]]
  [:todo/title [[e a v]] (= ?e e)]
  =>
  (println "BOOM")
  (insert! [?e :todo/visible :tag]))

;(defrule show-active
;  [Showing (= key :active)]
;  [?todos <- (acc/all) :from [Todo (= done false)]]
;  =>
;  (prn "show-active rule fired")
;  (insert! (->VisibleTodos ?todos)))
;
(defrule todo-is-visible-when-filter-active-and-todo-not-done
  ; when visibility filter set to "done"
  [:ui/visibility-filter [[e a v]] (= v :active)]
  ; and a todo
  [:todo/title [[e a v]] (= ?e e)]
  ; that isn't done (has no associated status)
  [:not [:todo/done [[e a v]] (= e ?e)]]
  =>
  (insert! [?e :todo/visible :tag]))

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
  [:not [:todo/done [[e a v]] (= e ?e)]]
  =>
  (println "Mark done via toggle complete:" ?e)
  (insert-unconditional! [?e :todo/done :done]))
; and todos/status :done count === num of todos

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
;(defquery find-showing
;  []
;  [?showing <- Showing])
;

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Queries
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn clara-tups->maps
  "Takes seq of ms with keys :?e :?a :?v, joins on :?e and returns
  vec of ms (one m for each entity)"
  [tups]
  (->> (group-by :?e tups)
    (mapv (fn [[id ent]]
            (into {:db/id id}
              (reduce (fn [m tup] (assoc m (:?a tup) (:?v tup)))
                {} ent))))))

(defn entity-tuples->entity-map
  "Takes list of tuples for a *single* entity and returns single map"
  [tups]
  (reduce
    (fn [acc [e a v]]
      (merge acc {:db/id e
                  a      v}))
    {} tups))

(defquery find-by-attribute-
  [:?a]
  [:all [[e a v]] (= e ?e) (= a ?a) (= v ?v)])

(defn find-by-attribute [session kw]
  (clara-tups->maps
    (query session find-by-attribute- :?a kw)))
;(defquery find-visible-todos
;  []
;  [?visible-todos <- VisibleTodos])
;
(defquery qav-
  "(Q)uery (A)ttribute (V)alue.
  Finds facts matching args attribute and value"
  [:?a :?v]
  [:all [[e a v]] (= e ?e) (= a ?a) (= v ?v)])
(defn qav [session a v]
  (clara-tups->maps
    (query session qav- :?a a :?v v)))
(defquery qave-
  "(Q)uery (A)ttribute (V)alue (E)ntity.
  Finds facts matching args attribute, value and eid"
  [:?a :?v :?e]
  [:all [[e a v]] (= e ?e) (= a ?a) (= v ?v)])
(defn qave [session a v e]
  (clara-tups->maps
    (query session qav- :?a a :?v v :?e e)))
;(defquery find-todos
;  []
;  [?todos <- (acc/all) :from [Todo]])
;
;(defquery find-todo
;  [:?id])
;  [?todo <- Todo (= id ?id)])
;
(defquery entity- [:?eid]
  [?entity <- :all [[e a v]] (= e ?eid)])

(defn entity [session id]
  (entity-tuples->entity-map
    (mapv :?entity (query session entity- :?eid id))))

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
  [:all [[e a v]] (= e ?e) (= a ?a) (= v ?v)]
  [:test (= (attr-ns ?a) "todo")])


;(defquery find-done-count
;  []
;  [?count <- (acc/count) :from [Todo (= done true)]])
;
(defquery find-done-count []
  [?count <- (acc/count) :from [:todo/done [[e a v]] (= v :done)]])


(defsession todos 'todomvc.rules
  :fact-type-fn (fn [[e a v]] a)
  :ancestors-fn (fn [type] [:all]))

(def facts
  (apply concat
    [[(random-uuid) :today/is-friday :tag]]
    (map->tuple (toggle-tx (random-uuid) true))
    (map->tuple (visibility-filter-tx (random-uuid) :all))
    (mapv map->tuple (repeatedly 5 #(todo-tx (random-uuid) "TODO" nil)))))


(def session (fire-rules (insert-all todos facts)))

(def all-done (query session find-all-done))


;(cljs.pprint/pprint (clara-tups->maps all-done))

;(println "Done count: " (query session find-done-count))


(cljs.pprint/pprint (entity session (:db/id (first (clara-tups->maps all-done)))))
(cljs.pprint/pprint  all-done)

(cljs.pprint/pprint (find-by-attribute session :ui/visibility-filter))

(defn entities-where
  "Returns hydrated entities matching an attribute-only or an attribute-value query"
  ([session a] (map #(entity session (:db/id %)) (find-by-attribute session a)))
  ([session a v] (map #(entity session (:db/id %)) (qav session a v)))
  ([session a v e] (map #(entity session (:db/id %)) (qave session a v e))))

;(cljs.pprint/pprint (map #(entity session (:db/id %)) (qav session :todo/done :done)))
(cljs.pprint/pprint (entities-where session :todo/visible :tag))
