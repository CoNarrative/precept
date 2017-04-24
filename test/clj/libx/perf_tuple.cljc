(ns libx.perf-tuple
    (:require [libx.util :refer [guid
                                 insert
                                 insert!
                                 insert-unconditional!
                                 ->Tuple
                                 retract!] :as util]
              [clara.rules :as cr]
              [clara.rules.accumulators :as acc]
              [libx.spec.sub :as sub]
              [libx.core :refer [fact-id]]
              [libx.tuplerules :refer [def-tuple-session
                                       def-tuple-rule
                                       def-tuple-query]]
              [libx.listeners :as l])
    (:import [libx.util Tuple]))


;; TODO. Nice to use rules for this but probably faster to do this at creation time
;; for each fact
(def-tuple-rule add-fact-id
  {:super true :salience 100}
  [?fact <- [_ :all _ -1]]
  =>
  (println "Adding id to fact" (inc @fact-id))
  (retract! ?fact)
  (cr/insert-unconditional!
    (apply ->Tuple
      (conj (into [] (butlast (vals ?fact)))
        (swap! fact-id inc)))))

(def-tuple-rule report-two-facts-different-tx
  {:super true :salience 100}
  [?fact1 <- [?e ?a ?v ?t1]]
  [?fact2 <- [?e ?a ?v ?t2]]
  [:test (not= ?t1 ?t2)]
  =>
  (println "Two facts different tx"
    ?t1 ?t2
    ?fact1 ?fact2)
  (retract! (if (> ?t1 ?t2) ?fact2 ?fact1)))

(def-tuple-rule todo-is-visible-when-filter-is-all
  [[_ :ui/visibility-filter :all]]
  [[?e :todo/title]]
  =>
  (insert! [?e :todo/visible :tag]))

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
  (println "Inserting :todo/title")
  (insert-unconditional! [(guid) :todo/title ?title]))

(def-tuple-rule add-item-cleanup
  {:group :cleanup}
  [?action <- [:add-todo-action]]
  =>
  (println "Action cleanup")
  (retract! ?action))

(def-tuple-rule acc-all-visible
  {:group :report}
  [?count <- (acc/count) :from [:todo/title]]
  [:test (> ?count 0)]
  =>
  (println "Reporting count" ?count)
  (insert! [-1 :todo/count ?count]))

(def groups [:action :calculation :report :cleanup])
(def activation-group-fn (util/make-activation-group-fn :normal))
(def activation-group-sort-fn (util/make-activation-group-sort-fn groups :normal))

;(def-tuple-session tuple-session
;  'libx.perf-tuple
;  :activation-group-fn activation-group-fn
;  :activation-group-sort-fn activation-group-sort-fn)
(def tuple-session
  (cr/mk-session 'libx.perf-tuple
   :fact-type-fn (fn [fact]
                   (:a fact))
   :ancestors-fn (fn [type]
                   (println "Ancestors fn" type)
                   [:all])
   :activation-group-fn activation-group-fn
   :activation-group-sort-fn activation-group-sort-fn))

(defn n-facts-session [n]
  (-> tuple-session
    (insert (repeatedly n #(vector (guid) :todo/title "foobar")))))

(def state (atom (n-facts-session 10#_0000)))

(defn perf-loop [iters]
  (time
    (dotimes [n iters]
      (time
        (reset! state
          (-> @state
            ;(l/replace-listener)
            (insert [(guid) :add-todo-action "hey"])
            (cr/fire-rules)))))))

(perf-loop 1#_00)

;; agenda phases
;; schema maintenance should be high salience and always available
;; action
;; compute
;; report
;; cleanup

;;TODO. Remove or move to test
;(activation-group-fn {:props {:salience 100
;                              :group :cleanup)
;
;(activation-group-sort-fn {:group :schema :salience -100}
;                          {:group :cleanup :salience 100})
                          ;{:group "cleanup"})


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

