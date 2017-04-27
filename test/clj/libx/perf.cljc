(ns libx.perf
    (:require [clara.rules :refer [insert!
                                   insert-all!
                                   insert-unconditional!
                                   insert-all-unconditional!
                                   retract!]
               :as cr]
              [clara.rules.accumulators :as acc]
              [libx.spec.sub :as sub]
              [libx.util :refer [guid ->Tuple map->Tuple] :as util]
              [libx.schema :as schema]
              [libx.schema-fixture :refer [test-schema]]
              [clara.tools.inspect :as inspect])
    (:import [libx.util Tuple]))

(defn fact [[e a v t]]
  (->Tuple e a v (or t -1)))

(cr/defrule todo-is-visible-when-filter-is-all
  [Tuple (= a :ui/visibility-filter) (= v :all)]
  [Tuple (= e ?e) (= a :todo/title)]
  =>
  (insert! (fact [?e :todo/visible :tag])))
;
(cr/defrule todo-is-visile-when-filter-is-done-and-todo-done
  [Tuple (= a :ui/visibility-filter) (= v :done)]
  [Tuple (= e ?e) (= a :todo/done)]
  =>
  (insert! (fact [?e :todo/visible :tag])))
;;
(cr/defrule todo-is-visible-when-filter-active-and-todo-not-done
  [Tuple (= a :ui/visibility-filter) (= v :active)]
  [Tuple (= e ?e) (= a :todo/title)]
  [:not [Tuple (= e ?e) (= a :todo/done)]]
  =>
  (insert! (fact [?e :todo/visible :tag])))
;
(cr/defrule toggle-all-complete
  [:exists [Tuple (= a :ui/toggle-complete)]]
  [Tuple (= e ?e) (= a :todo/title)]
  [:not [Tuple (= e ?e) (= a :todo/done)]]
  =>
  (insert-unconditional! (fact [?e :todo/done :tag])))

(cr/defrule add-item-handler
  [Tuple (= a :add-todo-action) (= v ?title)]
  =>
  (insert-unconditional! (fact [(guid) :todo/title ?title])))

(cr/defrule add-item-cleanup
  {:group :cleanup}
  [?action <- Tuple (= a :add-todo-action)]
  =>
  (retract! ?action))

;(cr/defrule acc-all-visible
;  [?count <- (acc/count) :from [Tuple (= ?e e) (= a :todo/title)]]
;  [:test (> ?count 0)]
;  =>
;  (insert! (fact [-1 :todo/count ?count])))

;(cr/defrule remove-older-unique-identity-facts
;  {:super true :salience 100}
;  [Tuple (= ?a :unique-identity) (= ?e e) (= ?t1 t)]
  ;[?fact2 <- Tuple (= ?e e) (= ?a e) (= ?t2 t)]
  ;[:test (> ?t1 ?t2)]
  ;=>
  ;(do))
  ;(retract! ?fact2))
  ;(println "unqiue identity" ?e ?t1 ?t2))
  ;(println (str "SCHEMA MAINT - :unique-identity" ?t1 " is greater than " ?t2))
  ;(retract! ?fact2))

;(ns-unmap *ns* 'remove-older-unique-identity-facts)

(def groups [:action :calc :report :cleanup])
(def activation-group-fn (util/make-activation-group-fn :calc))
(def activation-group-sort-fn (util/make-activation-group-sort-fn groups :calc))
(def hierarchy (schema/schema->hierarchy test-schema))
(def ancestors-fn (util/make-ancestors-fn hierarchy Tuple))

(cr/defsession cr-session 'libx.perf
  :fact-type-fn :a
  :ancestors-fn ancestors-fn
  :activation-group-fn activation-group-fn
  :activation-group-sort-fn activation-group-sort-fn)
  ;:activation-group-fn :salience
  ;:activation-group-sort-fn >)
;(user/reload)

(defn n-facts-session [n]
  (-> cr-session
    (cr/insert-all (repeatedly n #(fact [(guid) :todo/title "foobar"])))))

(def state (atom (n-facts-session 10#_0000)))

(defn perf-loop [iters]
  (time
    (dotimes [n iters]
      (time
        (reset! state
          (-> @state
            (cr/insert (fact [1 :done-count 6 1]))
            (cr/insert (fact [1 :done-count 7 2]))
            (cr/insert (fact [(guid) :add-todo-action "hey"]))
            (cr/fire-rules)))))))

(perf-loop 1#_00)

;(inspect/explain-activations @state)
;;
;; No activation group / sort
;; load file 2086ms
;; loop 71ms