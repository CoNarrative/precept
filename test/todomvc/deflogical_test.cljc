(ns todomvc.deflogical-test
    (:require [clojure.test :refer [deftest run-tests is testing]]
              [todomvc.tuplerules :refer [deflogical]]
              [clara.rules :refer [defrule]]))

(deftest deflogical-test
  (testing "Single fact"
    (is (= (macroexpand
             '(deflogical my-logical
                [[-1 :foo "bar"]]
                [[?e :baz]]
                [[?e :quux]]))
           (macroexpand
             '(defrule my-logical
                [:baz [[e a v]] (= ?e e)]
                [:quux [[e a v]] (= ?e e)]
                =>
                (insert! [-1 :foo "bar"]))))))
  (testing "Multiple facts"
    (is (= (macroexpand
             '(deflogical my-logical
                [[-1 :foo "bar"] [-2 :foo "baz"]]
                [[?e :baz]]
                [[?e :quux]]))
          (macroexpand
            '(defrule my-logical
               [:baz [[e a v]] (= ?e e)]
               [:quux [[e a v]] (= ?e e)]
               =>
               (insert-all! [[-1 :foo "bar"]
                             [-2 :foo "baz"]])))))
   (testing "Support"
    (is (= (macroexpand
             '(deflogical isa-bird :support
                [[?e :bird]]
                [[?e :has-wings]]))
          (macroexpand
            '(defrule my-logical
               [:baz [[e a v]] (= ?e e)]
               [:quux [[e a v]] (= ?e e)]
               =>
               (insert-all! [[-1 :foo "bar"]
                             [-2 :foo "baz"]]))))))))
(macroexpand
  '(deflogical thing-is-a-bird :support
     [[?e :isa-bird]]
     [[?e :has-wings]]))
; Gets rewritten to:
; [[?e :has-wings]]
; =>
; (insert! [?e :tuplerules.support/thing-is-a-bird [?e :isa-bird]]

'(deflogical thing-is-a-bird :support
   [[?e :isa-bird]]
   [[?e :flies]])
; Gets rewritten to:
; [[?e :flies]]
; =>
; (insert! [?e :tuplerules.support/thing-is-a-bird [?e :isa-bird]]

'(deflogical thing-is-a-bird :veto
   [[?e :isa-bird]]
   [[?e :metal]])
; Gets rewritten to:
; [[?e :metal]]
; =>
; (insert! [?e :tuplerules.veto/thing-is-a-bird :tag])

'(def-tuple-rule thing-is-a-bird-decider
   [:exists [?e :tuplerules.support/thing-is-a-bird ?fact]]
   [:not [?e :tuplerules.veto/thing-is-a-bird]]
   =>
   (insert! ?fact))
; We insert this somewhere


; Notes:
; "tuplerules" = {name of our lib here}. Intended as a placeholder for our ns that won't conflict
; with something the user's keys or those of another lib (Datomic and datascript use :db/ for
; example) "thing-is-a-bird" in keywords = {name of rule here}. Means we will need to avoid creation
; of "decider" for each deflogical (limit 1 per name)


; Questions:
; - Do we always need to join on entity id? Seems that way, even if user does not specify
;   a join on it. Otherwise seems like we wouldn't know what fact we/they are talking about


(run-tests)