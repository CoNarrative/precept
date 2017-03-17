(ns todomvc.rules-test
    (:require [cljs.test :refer-macros [deftest is testing run-tests]]
              [todomvc.rules]))


(deftest mark-all-done
  (is (= 1 1)))

(run-tests *ns*)