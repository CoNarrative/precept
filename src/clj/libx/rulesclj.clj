(ns libx.rulesclj
    ;(:require [libx.tuplerules :refer [def-tuple-rule]]
    ;          [clara.rules :refer [defrule]]
    ;          [clojure.test :refer [deftest is run-tests]]
    ;          [clojure.spec :as s]
;(macroexpand
;  '(def-tuple-rule foo
;     [[?e :todo/done]]
;     =>
;     (println "Hey")))
;
;(def-tuple-rule foo
;  [[?e :todo/done]]
;  =>
;  (println "Hey"))
;
;(deftest foo
;  (is (= (macroexpand
;           '(def-tuple-rule foo
;               [[?e :todo/done]]
;               =>
;               (println "Hey")))
;         (macroexpand
;           '(defrule foo
;              [:todo/done [[e a v]] (= ?e e)]
;              =>
;              (println "Hey"))))))
;
;
;(s/check-asserts true)
;(run-tests)
    (:require [clara.rules :refer [insert!
                                   insert-all
                                   insert-all!
                                   insert-unconditional!
                                   insert-all-unconditional!
                                   retract! query fire-rules
                                   mk-session
                                   defrule defquery defsession]]
              [libx.tuplerules :refer [def-tuple-session def-tuple-rule]]
              [libx.util :refer [retract
                                 insert
                                 map->tuples
                                 entities-where
                                    attr-ns]]
              [clara.rules.accumulators :as acc]
              [clara.tools.inspect :as inspect]
              [clara.tools.tracing :as trace]
              [clara.rules.compiler :as com]
              [clojure.tools.namespace.repl :refer [refresh]]))
;[clara.tools.watch :as watch]


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

(def-tuple-rule todo-is-visible-when-filter-is-all
  [[_ :ui/visibility-filter :all]]
  [[?e :todo/title]]
  =>
  (insert! [?e :todo/visible :tag]))

;(defrule todo-is-visible-when-filter-is-done-and-todo-done
;  [:ui/visibility-filter [[e a v]] (= v :done)]
;  [:todo/done [[e a v]] (= e ?e)]
;  =>
;  (insert! [?e :todo/visible :tag]))
;
;(defrule todo-is-visible-when-filter-active-and-todo-not-done
;  [:ui/visibility-filter [[e a v]] (= v :active)]
;  [:todo/title [[e a v]] (= ?e e)]
;  [:not [:todo/done [[e a v]] (= e ?e)]]
;  =>
;  (insert! [?e :todo/visible :tag]))

;(defrule toggle-all-complete
;  ; when toggle complete action exists
;  [:exists [:ui/toggle-complete]]
;  ; and there's a todo that isn't marked "done"
;  [:todo/title [[e a v]] (= ?e e)]
;  [:not [:todo/done [[e a v]] (= ?e e)]]
;  =>
;  (println "Marked done via toggle complete:" ?e)
;  (insert-unconditional! [?e :todo/done :done]))
;
;(defrule remove-toggle-complete-when-all-todos-done
;  [?toggle <- :ui/toggle-complete [[e a v]] (= v true)]
;  [?total <- (acc/count) :from [:todo/title]]
;  [?total-done <- (acc/count) :from [:todo/done]]
;  [:test (not (not (= ?total ?total-done)))]
;  =>
;  (println "Total todos: " ?total)
;  (println "Total done: " ?total-done)
;  (println "Retracting toggle-all-complete action: " ?toggle)
;  (retract! ?toggle))

;(defrule no-done-todos-when-clear-completed-action
;  ; when clear completed action exists
;  [:exists [:ui/clear-completed]]
;  ; and there's a todo that is marked "done"
;  [:todo/title [[e a v]] (= ?e e)]
;  [:todo/done [[e a v]] (= ?e e)]
;  [?entity <- (acc/all) :from [:all [[e a v]] (= ?e e)]]
;  =>
;  ; (retract {:todo/keys :all})
;  ; (retract {:todo/keys [title visible done]})
;  ; (retract-entity ?e)
;  ;; can we run a query in RHS???
;  (println "Retracting entity " ?entity)
;  (doseq [tuple ?entity] (retract! tuple)))
;
;(defrule clear-completed-action-is-done-when-no-done-todos
;  [?action <- :ui/clear-completed]
;  [:not [:exists [:todo/done]]]
;  =>
;  (println "Clear-completed action finished. Retracting " ?action)
;  (retract! ?action))

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

;(defrule print-all-facts
;  [?fact <- :all [[e a v]] (= ?e e) (= ?a a) (= ?v v)]
;  =>
;  (println "FACT" ?fact))
;
;(defrule mytuplerule
;  [:all [[e a v]] (= ?v v) (= ?e e)]
;  [:all [[e a v]] (= a ?v) (= ?e2 e)]
;  [:test (= ?e2 ?e)]
;  =>
;  (println "Found :entity whose value is the same as one of its attributes!"))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Queries
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;(defquery find-all-done []
;  [:all [[e a v]] (= e ?e) (= a ?a) (= v ?v)]
;  [:test (= (attr-ns ?a) "todo")])
;
;(defquery find-done-count []
;  [?count <- (acc/count) :from [:todo/done]])

;(defsession todos 'libx.rulesclj
;  :fact-type-fn (fn [[e a v]] a)
;  :ancestors-fn (fn [type] [:all]))

;(def empty-session
;  (mk-session 'libx.rulesclj
;    :fact-type-fn (fn [x] (second x))
;    :ancestors-fn (fn [type] [:all])))

;(def facts
;  (apply concat
;    (map->tuples (toggle-tx (random-uuid) true))
;    (map->tuples (visibility-filter-tx (random-uuid) :active))
;    (mapv map->tuples (repeatedly 5 #(todo-tx (random-uuid) 1111 nil)))))
;
;
;(def wfacts (fire-rules (insert-all empty-session facts)))
;
;(-> [wfacts] clara.rules.compiler/to-beta-graph clojure.pprint/pprint)
;(-> [wfacts] clara.rules.compiler/to-alpha-graph clojure.pprint/pprint)
;(inspect/inspect wfacts)

;(defrecord Entity [e a v])

;(defrule myrule
;  [Entity (= ?v v) (= ?e e)]
;  [Entity (= a ?v) (= ?e2 e)]
;  [:test (= ?e2 ?e)]
;  =>
;  (println "Found Entity whose value is the same as one of its attributes!"))


;(->Entity 1 2 3)

;(def record-session
;  (->
;    (mk-session [myrule])
;    (trace/with-tracing)
;    (insert-all (into
;                    [(->Entity 123 :hi "there")
;                     (->Entity 123 :oh :hi)
;                   (repeatedly 5 #(->Entity "null" true "null"))
;    (fire-rules))
;(-> [record-session] clara.rules.compiler/to-beta-graph clojure.pprint/pprint)
;(-> [record-session] clara.rules.compiler/to-alpha-graph clojure.pprint/pprint)
;(inspect/inspect record-session)
;(inspect/explain-activations record-session)
;(trace/get-trace record-session)
;(-> [record-session] com/to-beta-graph clojure.pprint/pprint)
;;;;;;;;;;;;;;;

(def-tuple-rule test-insert-l-retract-l
  [?f <- [?e :attr/a]]
  =>
  (println "Found :attr/a" ?f)
  (insert! [?e :attr/logical-insert ?f]))

(def-tuple-rule foooo
  [[_ :attr/logical-insert ?f]]
  =>
  (println "Found " ?f " Retracting its condition for existing")
  (retract! ?f))

(def-tuple-session the-session 'libx.rulesclj)

(def state-0
  (-> the-session
    (trace/with-tracing)
    (insert-all (into
                    [[123 :attr/a "state-0"]
                     [123 :attr/a "state-0"]
                     [123 :attr/b "state-0"]]
                 (repeatedly 5 #(vector (java.util.UUID/randomUUID) :junk 42))))
    (fire-rules)))

;(-> [tuple-session] clara.rules.compiler/to-beta-graph clojure.pprint/pprint)
;(-> [tuple-session] clara.rules.compiler/to-alpha-tree clojure.pprint/pprint)
;(inspect/inspect tuple-session)
;(inspect/explain-activations tuple-session)

(def trace-0 (trace/get-trace state-0))

(defn trace-by-type [trace]
  (select-keys
    (group-by :type trace)
    [:add-facts :add-facts-logical :retract-facts :retract-facts-logical]))

(defn retractions [trace-by-type]
  (select-keys trace-by-type [:retract-facts :retract-facts-logical]))

(defn insertions [trace-by-type]
  (select-keys trace-by-type [:add-facts :add-facts-logical]))

(defn list-facts [xs]
  (mapcat :facts (mapcat identity (vals xs))))

(defn key-by-hashcode [coll]
  "WILL remove duplicates"
  (zipmap (map hash-ordered-coll coll) coll))

(defn select-disjoint [added removed]
  "Takes m keyed by hashcode. Returns same with removals applied to additions"
  (let [_ (println "Removed" (select-keys removed (set (keys removed))))
        a (set (keys added))
        b (set (keys removed))]
    (select-keys added (remove b a))))

(defn split-ops [trace]
  "Takes trace returned by Clara's get-trace. Returns m of :adds, :retracts"
  (let [by-type (trace-by-type trace)
        hashed-adds (key-by-hashcode (list-facts (insertions by-type)))
        hashed-retracts (key-by-hashcode (list-facts (retractions by-type)))]
    {:added (into [] (vals (select-disjoint hashed-adds hashed-retracts)))
     :retracted (into [] (vals hashed-retracts))}))

(split-ops trace-0)

(entities-where state-0 :attr/a)
(entities-where state-0 :attr/b)

(def state-1
  (-> state-0
    (trace/without-tracing)
    (trace/with-tracing)
    (retract [123 :attr/b "state-0"])
    (insert [123 :attr/b "state-1"])
    (fire-rules)))

(def trace-1 (trace/get-trace state-1))

(split-ops trace-1)

(entities-where state-1 :attr/a)
(entities-where state-1 :attr/b)

;(refresh)
