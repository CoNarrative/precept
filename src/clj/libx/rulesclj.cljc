;(ns libx.rulesclj
;   (:require [clara.rules :refer [insert!
;                                  insert-all
;                                  insert-all!
;                                  insert-unconditional!
;                                  insert-all-unconditional!
;                                  retract! query fire-rules
;                                  mk-session
;                                  defrule defquery defsession]]
;             [libx.tuplerules :refer [def-tuple-session def-tuple-rule]]
;             [libx.util :refer [retract
;                                 insert
;                                 map->tuples
;                                 entities-where
;                                 entity
;                                 attr-ns]]
;             [clara.rules.accumulators :as acc]
;             [clara.tools.inspect :as inspect]
;             [clara.tools.tracing :as trace]
;             [clara.rules.compiler :as com]
;             [clojure.tools.namespace.repl :refer [refresh]]
;             [clara.rules.engine :as eng]
;             [clara.rules.listener :as l])
;  (:import clara.tools.tracing.TracingListener))
;[clara.tools.watch :as watch]

;
;(defn random-uuid []
;  (java.util.UUID/randomUUID))

;(defn todo-tx [id title done]
;  (merge
;    {:db/id      id
;     :todo/title title
;    (when-not (nil? done)
;      {:todo/done done}))
;
;(defn visibility-filter-tx [id kw]
;  {:db/id                id
;   :ui/visibility-filter kw))
;
;(defn toggle-tx [id bool]
;  {:db/id              id
;   :ui/toggle-complete bool))
;
;(def clear-completed-action
;  {:db/id              (random-uuid)
;   :ui/clear-completed :tag))

;(def-tuple-rule todo-is-visible-when-filter-is-all
;  [[_ :ui/visibility-filter :all]]
;  [[?e :todo/title]]
;  =>
;  (insert! [?e :todo/visible :tag]))

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

;(-> [tuple-session] clara.rules.compiler/to-beta-graph clojure.pprint/pprint)
;(-> [tuple-session] clara.rules.compiler/to-alpha-tree clojure.pprint/pprint)
;(inspect/inspect tuple-session)
;(inspect/explain-activations tuple-session)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Libx additions
(ns ns.rulesclj
    (:require #?(:clj [clojure.core.async :refer [<!! >!! put! go close! chan <! >! go-loop]]
                 :cljs [cljs.core.async :refer [put! take! go close! chan <! >! go-loop]])
              [libx.util :refer [entity-tuples->entity-map]]
              [reagent.core :as r]
              [reagent.ratom :refer [make-reaction]]
              [reagent.dom.server :refer [render-to-string]]))
;; test
(def changes {:added [[123 :attr/a 42]
                      [123 :attr/b "x"]
                      [456 :attr/a "foo"]
                      [789 :attr/b "baz"]]
              :removed [[123 :attr/a 42]
                        [456 :attr/a "foo"]]})

(defn with-op [change op-kw]
  (mapv (fn [ent] (conj ent (vector (ffirst ent) :op op-kw)))
    (partition-by first change)))

(defn changes-with-op [changes]
  (let [added (:added changes)
        removed (:removed changes)]
    (mapv entity-tuples->entity-map
      (into (with-op added :add)
            (with-op removed :remove)))))


(changes-with-op changes)

;; Lib
(def registry (atom nil))

(def changes-chan (chan 1))

(defn register [id]
  (let [;ratom (r/atom nil) ; using regular atom for clj repl testing
        ratom (atom {})]
    (swap! registry assoc id ratom)
    ratom))

(defn subscribe [id]
  "Returns ratom"
  (register id))

(defn router [in-ch registry] ;; are both global?
  (go-loop []
    (if-let [changes (<! in-ch)]
      (let [id (:db/id changes)
            op (:op changes)
            ratom (get @registry id)]
        (println "Rec'd changes!" changes)
        ;(println "Ratom" @ratom)
        (if (= :add op)
          (do
            (println "Ratom" ratom)
            (swap! ratom merge (dissoc changes :op))
            (recur))
          (do (swap! ratom (fn [m] (apply dissoc m (keys (remove #(#{:op :db/id} (first %))
                                                           changes)))))
              (recur))))
      (recur))))

(defn create-router [changes-ch registry]
  (router changes-ch registry))

(def ^:dynamic *foo* (create-router changes-chan registry))
@registry
(subscribe 123)
(subscribe 456)
(subscribe 789)
(for [change (changes-with-op changes)]
  (put! changes-chan change))



;; App

(defn component [id]
  (let [state (subscribe id)]
    (fn []
      [:div @state])))
;; Tests


;(refresh)
