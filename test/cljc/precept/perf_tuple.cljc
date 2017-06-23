(ns precept.perf-tuple
    (:require [precept.util :refer [guid insert insert! insert-unconditional! retract!] :as util]
              [precept.schema-fixture :refer [test-schema]]
              [precept.state :as state]
              [precept.schema :as schema]
              [clara.rules :as cr]
              [clara.rules.accumulators :as acc]
              [precept.spec.sub :as sub]
              [precept.rules :refer [session
                                          define
                                          rule
                                          defquery]]
              [precept.listeners :as l]
              [precept.schema :as schema]
      #?(:clj [clara.tools.inspect :as inspect])
      #?(:clj [clara.tools.tracing :as t])))

(defn trace [& x]
  (comment (apply prn x)))

;; TODO. create fn to reset `rules` atom. As we've discovered this might even be nice to
;; have for non-generated rule names, because when we delete a rule or rename it, it's still in
;; the REPL and requires a restart or manual ns-unmap to clear. We could expose a function
;; that takes all nses in which they are rules and unmaps everything in them.
(define [?e :todo/visible :tag] :- [[_ :visibility-filter :all
                                       [[?e :todo/title]]]])

(cr/defrule add-item-handler
  ;; Works with maps
  ;[:add-todo-action [{title :title}] (= ?title title)]
  [:add-todo-action (= ?title (:title this))]
  ;; Does not work with maps
  ;[:add-todo-action (= ?title title)]
  ;[:add-todo-action (= ?title :title)]
  =>
  (trace "Inserting :todo/title")
  (insert-unconditional! [(guid) :todo/title ?title]))

(rule todo-is-visile-when-filter-is-done-and-todo-done
  [[_ :visibility-filter :done]]
  [[?e :todo/done]]
  =>
  (insert! [?e :todo/visible :tag]))

(rule todo-is-visible-when-filter-active-and-todo-not-done
  [[_ :visibility-filter :active]]
  [[?e :todo/title]]
  [:not [?e :todo/done]]
  =>
  (insert! [?e :todo/visible :tag]))

(rule toggle-all-complete
  [:exists [:ui/toggle-complete]]
  [[?e :todo/title]]
  [:not [?e :todo/done]]
  =>
  (insert-unconditional! [?e :todo/done :tag]))

(rule acc-all-visible
  {:group :report}
  [?count <- (acc/count) :from [:todo/title]]
  [:test (> ?count 0)]
  =>
  (trace "Reporting count" ?count)
  (insert! [-1 :todo/count ?count]))


(rule action-cleanup
  {:group :cleanup}
  [?actions <- (acc/all) :from [:action]]
  [:test (> (count ?actions) 0)]
  =>
  (trace "CLEANING actions" ?actions)
  (doseq [action ?actions]
    (cr/retract! action)))

(def groups [:action :calc :report :cleanup])
(def activation-group-fn (util/make-activation-group-fn :calc))
(def activation-group-sort-fn (util/make-activation-group-sort-fn groups :calc))
(def hierarchy (schema/schema->hierarchy test-schema))
(def ancestors-fn (util/make-ancestors-fn hierarchy))

(session tuple-session
  'precept.perf-tuple
  :ancestors-fn ancestors-fn
  :activation-group-fn activation-group-fn
  :activation-group-sort-fn activation-group-sort-fn)

(defn n-facts-session [n]
  (let [s1 (insert tuple-session (repeatedly n #(vector (guid) :1-to-1 "foobar")))]
      (cr/fire-rules s1)))
;; Weirdly, if we insert facts but don't fire rules before attaching a listener
;; the facts that we insert  are not included in the session the session
;; on the next remove/add listener. There's actually a few ways this can occur
;; depending on when/where listeners are added and removed. Might
;; be nice to file a bug with Clara so they're aware.

(def session (atom (n-facts-session 100000)))

;(t/get-trace @session)
;(get-in (inspect/inspect @session) [:rule-matches remove-older-one-to-one-facts])
;; 246ms without, 5000ms with
;; UPDATE: 264ms with fact-index

;(reset! state/fact-index {})
;@state/fact-index

(defn perf-loop [iters]
  ;(time
    (dotimes [n iters]
      ;(time
        (reset! session
          (-> @session
            (l/replace-listener)
            ;(util/insert-action [(guid) :add-todo-action-2 {:todo/title "ho"}])
            ;(util/insert-action [(guid) :add-todo-action {:title "hey"}])
            (insert [[1 :done-count 5]
                     [1 :done-count 6]])
            (cr/fire-rules)))))

(time (perf-loop 100))
(l/vec-ops @session)
(count (:one-to-one @state/fact-index))
(:unique @state/fact-index)
;(inspect/inspect @session)
;(inspect/explain-activations @session)

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

