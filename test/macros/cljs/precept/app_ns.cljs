(ns precept.app-ns
  (:require [precept.rules :refer [rule define session q defquery fire-rules]]
            [precept.util :as util]
            [precept.repl :as repl :refer-macros [reload-session-cljs!]]
            [precept.accumulators :as acc]
            [precept.state :as state]))


(enable-console-print!)

;(rule hello-world
;  [?fact <- [_ :foo]]
;  =>
;  (.log js/console "1" ?fact)
;  (util/insert! [1 :duplicate-fact-error 1]))
;

;(rule next-rule
;  [?fact <- [_ :duplicate-fact-error]]
;  =>
;  (.log js/console "3 ---------- " ?fact)
;  (util/insert! [1 :xyz 1]))

;(rule report-facts-at-start
;  {:group :action}
;  [?fact <- [_ :all]]
;  =>
;  (println "<<<<<<<<<<<<<Fact at start>>>>>>>>>>>>>>>>" ?fact))

(rule report-facts-at-end
  {:group :report}
  [?facts <- (acc/all) :from [_ :all]]
  =>
  (println "All facts" ?facts))

(defquery everything []
  [?facts <- (acc/all) :from [_ :all]])

(define [?e :fact 3] :- [[?e :foo]])

(session my-session 'precept.app-ns)
(reload-session-cljs! 'my-session)

;@precept.state/session-defs
@state/fact-id
@state/fact-index
@precept.state/rules
@precept.state/unconditional-inserts
(ns-interns 'precept.app-ns)
;(q everything my-session)

(defn main []
  (enable-console-print!)
  (let [x (-> my-session
            (util/insert [:transient :foo "bar"])
            (fire-rules)
            (q everything))]
    (println "res" x)
    (println @state/rules)))

;(main)

;; Successful result combo of
;; uninterning rule in ns
;; dissocing all ::productions
;; combo of evaluating ns-unmap and "some macro" (which does dissoc)

;; 1. comment out rule
;; 2. ns-unmap rule
;; 3. evaluate "some-macro" with namespace name (ns name arg not used --
;; dissoces all productions)

;; second approach
;; Just have some-macro below (session) (not sure why this is)
;; change code (remove/add rule)
;; save file
