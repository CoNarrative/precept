(ns libx.perf
    (:require [clara.rules :refer [insert! insert-all! insert-unconditional! insert-all-unconditional!
                                   retract!] :as cr]
              [clara.rules.accumulators :as acc]
              [libx.spec.sub :as sub]
              [libx.util :refer [guid]]))

(defrecord Tuple [e a v])

(cr/defrule todo-is-visible-when-filter-is-all
  [Tuple (= a :ui/visibility-filter) (= v :all)]
  [Tuple (= e ?e) (= a :todo/title)]
  =>
  (insert! (->Tuple ?e :todo/visible :tag)))
;
(cr/defrule todo-is-visile-when-filter-is-done-and-todo-done
  [Tuple (= a :ui/visibility-filter) (= v :done)]
  [Tuple (= e ?e) (= a :todo/done)]
  =>
  (insert! (->Tuple ?e :todo/visible :tag)))
;;
(cr/defrule todo-is-visible-when-filter-active-and-todo-not-done
  [Tuple (= a :ui/visibility-filter) (= v :active)]
  [Tuple (= e ?e) (= a :todo/title)]
  [:not [Tuple (= e ?e) (= a :todo/done)]]
  =>
  (insert! (->Tuple ?e :todo/visible :tag)))
;
(cr/defrule toggle-all-complete
  [:exists [Tuple (= a :ui/toggle-complete)]]
  [Tuple (= e ?e) (= a :todo/title)]
  [:not [Tuple (= e ?e) (= a :todo/done)]]
  =>
  (insert-unconditional! (->Tuple ?e :todo/done :tag)))

(cr/defrule add-item-handler
  [Tuple (= a :add-todo-action) (= v ?title)]
  =>
  (insert-unconditional! (->Tuple (guid) :todo/title ?title)))

(cr/defrule add-item-cleanup
  {:salience -100}
  [?action <- Tuple (= a :add-todo-action)]
  =>
  (retract! ?action))

(cr/defrule acc-all-visible
  [?count <- (acc/count) :from [Tuple (= ?e e) (= a :todo/title)]]
  [:test (> ?count 0)]
  =>
  (insert! (->Tuple -1 :todo/count ?count)))

(cr/defsession cr-session 'libx.perf)

(defn n-facts-session [n]
  (-> cr-session
    (cr/insert-all (repeatedly n #(->Tuple (guid) :todo/title "foobar")))))

(def state (atom (n-facts-session 100000)))

(defn perf-loop [iters]
  (time
    (dotimes [n iters]
      (time
        (reset! state
          (-> @state
            (cr/insert (->Tuple (guid) :add-todo-action "hey"))
            (cr/fire-rules)))))))

(perf-loop 100)
