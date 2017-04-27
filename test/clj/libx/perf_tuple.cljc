(ns libx.perf-tuple
    (:require [libx.util :refer [guid
                                 insert
                                 insert!
                                 insert-unconditional!
                                 ->Tuple
                                 retract!] :as util]
              [libx.schema-fixture :refer [test-schema]]
              [libx.state :as state]
              [libx.schema :as schema]
              [clara.rules :as cr]
              [clara.rules.accumulators :as acc]
              [libx.spec.sub :as sub]
              [clara.tools.inspect :as inspect]
              [libx.tuplerules :refer [store-action
                                       def-tuple-session
                                       deflogical
                                       def-tuple-rule
                                       def-tuple-query]]
              [libx.listeners :as l]
              [libx.schema :as schema]
              [clara.tools.inspect :as inspect])
    (:import [libx.util Tuple]))

(defn trace [& x] (comment (apply prn x)))

;; Questions
;; TODO. create fn to reset `rules` atom. As we've discovered this might even be nice to
;; have for non-generated rule names, because when we delete a rule or rename it, it's still in
;; the REPL and requires a restart or manual ns-unmap to clear. We could expose a function
;; that takes all nses in which they are rules and unmaps everything in them.
(deflogical [?e :todo/visible :tag] :- [[_ :ui/visibility-filter :all]]
                                       [[?e :todo/title]])

(store-action :add-todo-action-2)

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
  (insert-unconditional! [?e :todo/done :tag]))

(def-tuple-rule add-item-handler
  [[_ :add-todo-action ?title]]
  =>
  (trace "Inserting :todo/title")
  (insert-unconditional! [(guid) :todo/title ?title]))

(cr/defrule remove-older-unique-identity-facts
  {:super true :salience 100}
  [:unique-identity (= ?e1 (:e this)) (= ?a1 (:a this)) (= ?t1 (:t this))]
  [?fact2 <- :unique-identity (= ?e1 (:e this)) (= ?a1 (:a this)) (= ?t2 (:t this))]
  [:test (> ?t1 ?t2)]
  =>
  (trace (str "SCHEMA MAINT - :unique-identity" ?t1 " is greater than " ?t2))
  (retract! ?fact2))

;(def-tuple-rule remove-older-unique-identity-facts
;  {:super true :salience 100}
;  [[?e :unique-identity _ ?t1]]
;  [[?e ?a _ ?t1]]
;  [?fact2 <- [?e ?a _ ?t2]]
;  [:test (> ?t1 ?t2)]
;  =>
;  (trace (str "SCHEMA MAINT - :unique-identity" ?t1 " is greater than " ?t2))
;  (retract! ?fact2))

(def-tuple-rule acc-all-visible
  {:group :report}
  [?count <- (acc/count) :from [:todo/title]]
  [:test (> ?count 0)]
  =>
  (trace "Reporting count" ?count)
  (insert! [-1 :todo/count ?count]))


(def-tuple-rule action-cleanup
  {:group :cleanup}
  [?actions <- (acc/all) :from [:action]]
  =>
  (trace "CLEANING actions" ?actions)
  (retract! (:actions ?actions)))

(def groups [:action :calc :report :cleanup])
(def activation-group-fn (util/make-activation-group-fn :calc))
(def activation-group-sort-fn (util/make-activation-group-sort-fn groups :calc))
(def hierarchy (schema/schema->hierarchy test-schema))
(def ancestors-fn (util/make-ancestors-fn hierarchy))

;(def-tuple-session tuple-session
;  'libx.perf-tuple
;  :activation-group-fn activation-group-fn
;  :activation-group-sort-fn activation-group-sort-fn)

(def tuple-session
  (cr/mk-session 'libx.perf-tuple
   :fact-type-fn :a
   :ancestors-fn ancestors-fn
   :activation-group-fn activation-group-fn
   :activation-group-sort-fn activation-group-sort-fn))

(defn n-facts-session [n]
  (-> tuple-session
    (insert (repeatedly n #(vector (guid) :todo/title "foobar")))))

(def session (atom (n-facts-session 100000)))
;(inspect/explain-activations @state)
(defn perf-loop [iters]
  (time
    (dotimes [n iters]
      (time
        (reset! session
          (-> @session
            ;(l/replace-listener)
            (util/insert-action [(guid) :add-todo-action-2 {:todo/title "hey"}])
            (insert [(guid) :add-todo-action "hey"])
            (insert [1 :done-count 6])
            (insert [1 :done-count 7])
            (cr/fire-rules)))))))
@state/rules
(perf-loop 100)

(libx.tuplerules/store-action :input/key-code-action)
;; agenda phases
;; schema maintenance should be high salience and always available
;; action
;; compute
;; report
;; cleanup


;; Timings - all our stuff
;; 100,000 facts
;; 100 iterations
;;
;; ~~~~~~~~~~~~~~~~~~NO AGENDA GROUPS~~~~~~~~~~~~~~~~~~~~~~~~~~
;; ~~~~~~~~~~~~~~~~~~add-item-cleanup NOT FIRING~~~~~~~~~~~~~~~
;; ~~~~No queries~~~~
;; loop                 47ms
;; loading file       1776ms
;;
;; ~~~~Including queries in the session def~~~~
;; qav-
;;     loop              47ms
;;     loading file    2086ms
;;
;; entity-
;;     loop            1384ms
;;     loading file    3330ms
;;
;; ~~~~Tracing, new one every loop, no queries~~~~
;; loop                  74ms
;; loading file          22ms (wtf?)
;;
;; ~~~~~~~~~~~~~~~~~~AGENDA GROUPS~~~~~~~~~~~~~~~~~~~~~~~~~~
;; ~~~~~~~~~~~~~~~~~~add-item-cleanup NOT FIRING~~~~~~~~~~~~~~~
;; ~~~~No queries~~~~
;; loop                 42ms
;; loading file       4359ms
;;
;; ~~~~~~~~~~~~~~~~~~add-item-cleanup FIRING~~~~~~~~~~~~~~~
;; ~~~~No queries~~~~
;; loop                 70ms
;; loading file       4446ms
;;
;;
;;*********************************************************
;;*********************************************************
;; ~~~~~~~~~~~~~~~~~~~~~~ CLJS ~~~~~~~~~~~~~~~~~~~~~~~~~~~

;; ~~~~~~~~~~~~~~~~~~AGENDA GROUPS~~~~~~~~~~~~~~~~~~~~~~~~~~
;; ~~~~~~~~~~~~~~~~~~add-item-cleanup NOT FIRING~~~~~~~~~~~~~~~
;; ~~~~No queries~~~~
;; loop                137ms
;; loading file      24388ms (bc :cache false in cljsbuild?)
;
;; ~~~~~~~~~~~~~~~~~~add-item-cleanup FIRING~~~~~~~~~~~~~~~
;; ~~~~No queries~~~~
;; loop                185ms
;; loading file      21199ms (bc :cache false in cljsbuild?)

