(ns libx.perf
    (:require [clara.rules ;:refer [insert!
                           ;       insert-all!
                           ;       insert-unconditional!
                           ;       insert-all-unconditional!
                           ;       retract!
               :as cr]
              [clara.rules.accumulators :as acc]
              [libx.spec.sub :as sub]
              [libx.util :refer [guid ->Tuple map->Tuple] :as util]
              [libx.schema :as schema]
              [libx.schema-fixture :refer [test-schema]]
              [clara.tools.inspect :as inspect])
    (:import [libx.util Tuple]))

(defn trace [& args]
  (comment (apply prn args)))

(cr/defrule todo-is-visible-when-filter-is-all
  ;[Tuple (= a :ui/visibility-filter) (= v :all)]
  [:ui/visibility-filter (= (:v this) :all)]
  ;[Tuple (= e ?e) (= a :todo/title)]
  [:todo/title (= (:e this) ?e)]
  =>
  (util/insert! [?e :todo/visible :tag]))
;
(cr/defrule todo-is-visile-when-filter-is-done-and-todo-done
  ;[Tuple (= a :ui/visibility-filter) (= v :done)]
  [:ui/visibility-filter (= (:v this) :done)]
  ;[Tuple (= e ?e) (= a :todo/done)]
  [:todo/done (= (:e this) ?e)]
  =>
  (util/insert! [?e :todo/visible :tag]))
;;
(cr/defrule todo-is-visible-when-filter-active-and-todo-not-done
  ;[Tuple (= a :ui/visibility-filter) (= v :active)]
  [:ui/visibility-filter (= (:v this) :active)]
  ;[Tuple (= e ?e) (= a :todo/title)]
  [:todo/title (= (:e this) ?e)]
  ;[:not [Tuple (= e ?e) (= a :todo/done)]]
  [:not [:todo/done (= (:e this) ?e)]]
  =>
  (util/insert! [?e :todo/visible :tag]))
;
(cr/defrule toggle-all-complete
  ;[:exists [Tuple (= a :ui/toggle-complete)]]
  [:exists [:ui/toggle-complete]]
  ;[Tuple (= e ?e) (= a :todo/title)]
  [:todo/title (= (:e this) ?e)]
  ;[:not [Tuple (= e ?e) (= a :todo/done)]]
  [:not [:todo/done (= (:e this) ?e)]]
  =>
  (util/insert-unconditional! [?e :todo/done :tag]))

(cr/defrule add-item-handler
  ;[Tuple (= a :add-todo-action) (= v ?title)]
  [:add-todo-action (= (:v this) ?title)]
  =>
  (util/insert-unconditional! [(guid) :todo/title ?title]))

(cr/defrule add-item-cleanup
  {:group :cleanup}
  ;[?action <- Tuple (= a :add-todo-action)]
  [?action <- :add-todo-action]
  =>
  (util/retract! ?action))

;(cr/defrule acc-all-visible
;  [?count <- (acc/count) :from [Tuple (= ?e e) (= a :todo/title)]]
;  [:test (> ?count 0)]
;  =>
;  (insert! (fact [-1 :todo/count ?count])))

(defn asc-fact-id
  ([]
   (acc/accum
     {:initial-value []
      :reduce-fn (fn [acc cur] (sort-by :t (conj acc cur)))
      :retract-fn (fn [acc cur] (sort-by :t (remove #(= cur %) acc)))}))
  ([k]
   (acc/accum
     {:initial-value []
      :reduce-fn (fn [acc cur] (sort-by :t (conj acc (k cur))))
      :retract-fn (fn [acc cur] (sort-by :t (remove #(= (k cur %)) acc)))})))

;(cr/defrule declare-one-to-one-attribute-names
;  [?types <- (acc/distinct :a) :from [:one-to-one]]
;  =>
;  (println "One to one types are: " ?types)
  ; Must be unconditional
  ;(util/insert-unconditional! (mapv #(vector (guid) :one-to-one/attr %) ?types)))

;(ns-unmap *ns* 'declare-one-to-one-attribute-names)

;(cr/defrule group-one-to-one-by-eid
;  [?by-eid <- (acc/grouping-by :e) :from [:one-to-one]]
;  =>
;  (trace ":one-to-one by eid" ?by-eid)
;  (doseq [[eid xs] ?by-eid]
;    (doseq [x (butlast xs)]
;      (util/retract! x))))

(cr/defrule remove-older-unique-identity-facts
  {:super true :salience 100}
  ;; Perf drops at 10k facts
  [?fact1 <- :one-to-one #_(= ?e1 (:e this)) (= ?a1 (:a this)) (= ?t1 (:t this))]
  [?fact2 <- :one-to-one #_(= ?e1 (:e this)) (= ?a1 (:a this)) (> ?t1 (:t this))]
  [:test (not= ?fact1 ?fact2)]
  =>
  (util/retract! ?fact2))

;  [?list <- (asc-fact-id) :from [:one-to-one]]
;  =>
;  (trace "Acc " ?list))
;  ;(println (str "SCHEMA MAINT - :unique-identity"))
;  ;(insert! [(guid) :remove-last-if-same-eid []])
;  ;(if (= (:e ?fact1) (:e ?fact2))
;  ;  (retract! ?fact2)
;  ;(do))
;;;; accumulate facts for same entity that are of type one to one and also the same


(def groups [:action :calc :report :cleanup])
(def activation-group-fn (util/make-activation-group-fn :calc))
(def activation-group-sort-fn (util/make-activation-group-sort-fn groups :calc))
(def hierarchy (schema/schema->hierarchy test-schema))
(def ancestors-fn (util/make-ancestors-fn hierarchy))

(cr/defsession cr-session 'libx.perf
  :fact-type-fn :a
  :ancestors-fn (fn [x] (println "Ancestor fn" x) (ancestors-fn x))
  :activation-group-fn activation-group-fn
  :activation-group-sort-fn activation-group-sort-fn)

(defrecord NonTuple [ex a vx fid])

(defn n-facts-session [n]
  (-> cr-session
    (util/insert (repeatedly n #(->Tuple (guid) :todo/title "foobar" (util/next-fact-id!))))))

(def state (atom (n-facts-session 10000#_0000#_000)))

(defn perf-loop [iters]
  (time
    (dotimes [n iters]
      (time
        (reset! state
          (-> @state
            (util/insert [1 :done-count 6])
            (util/insert [1 :done-count 7])
            (util/insert [(guid) :add-todo-action "hey"])
            (cr/fire-rules)))))))

(perf-loop 10#_00)


;(inspect/explain-activations @state)
;;
;; No activation group / sort
;; load file 2086ms
;; loop 71ms