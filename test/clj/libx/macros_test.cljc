(ns libx.macros-test
    (:require [libx.tuplerules :refer [def-tuple-session]]
              [clara.rules :refer [defsession]]
              [clojure.test :refer [deftest run-tests testing is]])
    (:import [libx.util Tuple]))

(deftest defn-tuple-session-test
  (testing "macroexpansion should be the same as equivalent arguments to defsession"
    (let [clara-session '(defsession foo 'libx.macros-test
                            :fact-type-fn :a
                            :ancestors-fn (fn [type] [:all]))
          wrapper       '(def-tuple-session foo 'libx.macros-test)]
      (is (= (macroexpand clara-session) (macroexpand wrapper))))))

(run-tests)
