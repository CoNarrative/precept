(ns libx.deflogical-test
    (:require [clojure.test :refer [deftest run-tests is testing]]
              [libx.tuplerules :refer [deflogical]]
              [clara.rules :refer [defrule]]))

(deftest deflogical-test
  (testing "Single fact"
    (is (=
          (macroexpand
             '(deflogical
                [-1 :foo "bar"] :-
                [[?e :baz]]
                [[?e :quux]]))
          (macroexpand
            '(defrule x
               [:baz (= ?e (:e this))]
               [:quux (= ?e (:e this))]
               =>
               (insert! [-1 :foo "bar"])))))))
  ;(testing "Multiple facts"
  ;  (let [output   (macroexpand
  ;                   '(deflogical
  ;                      [[-1 :foo "bar"] [-2 :foo "baz"]]
  ;                      [[?e :baz]]
  ;                      [[?e :quux]]))
  ;        expected (macroexpand
  ;                   '(defrule my-logical
  ;                      [:baz (= ?e (:e this))]
  ;                      [:quux (= ?e (:e this))]
  ;                      =>
  ;                      (insert-all! [[-1 :foo "bar"] [-2 :foo "baz"]])))]
  ;    (is (= output expected)))))


(run-tests)