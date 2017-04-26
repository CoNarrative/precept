(ns libx.macros-test
    (:require [libx.tuplerules :refer [def-tuple-session]]
              [clara.rules :refer [defsession]]
              [clojure.test :refer [deftest run-tests testing is]])
    (:import [libx.util Tuple]))

(deftest def-tuple-session-test
  (testing "Macroexpansion should be the same as equivalent
            arguments to defsession"
      (is (= (macroexpand '(defsession foo
                            'libx.macros-test
                            :fact-type-fn :a
                            :ancestors-fn (fn [type] [:all])))
             (macroexpand '(def-tuple-session foo 'libx.macros-test)))))

  (testing "Allow overwrite defaults"
    (let [clara-session '(defsession foo
                           'libx.macros-test
                           :fact-type-fn (fn [x] (or (:a x)
                                                     (:b x)))
                           :ancestors-fn (fn [x] [:all :foo]))
          wrapper       '(def-tuple-session foo
                           'libx.macros-test
                           :fact-type-fn (fn [x] (or (:a x)
                                                     (:b x)))
                           :ancestors-fn (fn [x] [:all :foo]))]
      (is (= (macroexpand clara-session) (macroexpand wrapper))))))

(run-tests)
