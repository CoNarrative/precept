(ns todomvc.macros-test
    (:require [todomvc.tuplerules :refer [def-tuple-session]]
              [clara.rules :refer [defsession]]
              [clojure.test :refer [deftest run-tests testing is]]))

(deftest defn-tuple-session-test
  (testing "macroexpansion should be the same as equivalent arguments to defsession"
    (let [clara-session '(defsession foo 'todomvc.util 'todomvc.macros-test
                            :fact-type-fn (fn [[e a v]] a)
                            :ancestors-fn (fn [type] [:all]))
          wrapper       '(def-tuple-session foo 'todomvc.macros-test)]
      (is (= (macroexpand clara-session) (macroexpand wrapper))))))

(run-tests)
