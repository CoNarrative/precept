(ns libx.perf-tuple
    (:require [libx.util :refer [guid
                                 insert
                                 insert!
                                 insert-unconditional!
                                 retract!]]
              [clara.rules :as cr]
              [clara.rules.accumulators :as acc]
              [libx.spec.sub :as sub]
              [libx.tuplerules :refer [def-tuple-session def-tuple-rule def-tuple-query]]
              [libx.listeners :as l]))
              ;[criterium.core :refer [bench]]))
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
  (insert-unconditional! [(guid) :todo/title ?title]))

(def-tuple-rule add-item-cleanup
  {:salience -100}
  [?action <- [:add-todo-action]]
  =>
  (retract! ?action))

(def-tuple-rule acc-all-visible
  [?count <- (acc/count) :from [?e :todo/title]]
  [:test (> ?count 0)]
  =>
  (insert! [-1 :todo/count ?count]))

(def-tuple-session tuple-session 'libx.perf-tuple)
;(cr/defsession tuple-session
;  'libx.perf-tuple
;   :fact-type-fn :a
;   :ancestors-fn (fn [type] [:all]))

(defn n-facts-session [n]
  (-> tuple-session
    (insert (repeatedly n #(vector (guid) :todo/title "foobar")))))

(def state (atom (n-facts-session 100000)))

(defn perf-loop [iters]
  (time
    (dotimes [n iters]
      (time
        (reset! state
          (-> @state
            ;(l/replace-listener)
            (insert [(guid) :add-todo-action "hey"])
            (cr/fire-rules)))))))

(perf-loop 100)
;(clojure.tools.namespace.repl/refresh)


;; Timings - all our stuff
;; 100,000 facts
;; 100 iterations
;;
;; ~~~~No queries~~~~
;; loop                47ms
;; loading file       1776ms
;;
;; ~~~~Including queries in the session def~~~~
;; qav-
;;     loop             47ms
;;     loading file    2086ms
;;
;; entity-
;;     loop            1384ms
;;     loading file    3330ms
;;
;; ~~~~Tracing, new one every loop, no queries~~~~
;; loop                  74ms
;; loading file          22ms (wtf?)
