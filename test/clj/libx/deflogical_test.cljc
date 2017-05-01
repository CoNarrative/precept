(ns libx.deflogical-test
    (:require [clojure.test :refer [deftest run-tests is testing]]
              [libx.tuplerules :refer [deflogical]]
              [clara.rules :refer [defrule]]))

(defn rule-props [expansion]
  (second (nth expansion 2)))

;; Because names for deflogical are generated we skip the part of the
;; expansion that includes the name
(deftest deflogical-test
  (testing "Single fact"
    (is (=
          (rule-props
            (macroexpand
               '(deflogical
                  [-1 :foo "bar"] :-
                  [[?e :baz]]
                  [[?e :quux]])))
          (rule-props
            (macroexpand
              '(defrule some-generated-name
                 [:baz (= ?e (:e this))]
                 [:quux (= ?e (:e this))]
                 =>
                 (libx.util/insert! [-1 :foo "bar"])))))))
  (testing "Multiple facts"
    (is (=
          (rule-props
            (macroexpand
             '(deflogical
                [[-1 :foo "bar"] [-2 :foo "baz"]] :-
                [[?e :baz]]
                [[?e :quux]])))
          (rule-props
            (macroexpand
              '(defrule some-generated-name
                 [:baz (= ?e (:e this))]
                 [:quux (= ?e (:e this))]
                 =>
                 (libx.util/insert! [[-1 :foo "bar"] [-2 :foo "baz"]]))))))))

(run-tests)
