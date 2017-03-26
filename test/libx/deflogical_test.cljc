(ns libx.deflogical-test
    (:require [clojure.test :refer [deftest run-tests is testing]]
              [libx.tuplerules :refer [deflogical]]
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
                             [-2 :foo "baz"]])))))))

(run-tests)