(ns precept.lang-test
    (:require [clojure.test :refer [deftest is testing run-tests]]
              [precept.spec.lang :as lang]
              [clojure.spec :as s]
              [clojure.spec.gen :as gen]))

(deftest lang-test
  (testing "Variable binding"
    (is (s/valid? ::lang/variable-binding '?foo))
    (is (not (s/valid? ::lang/variable-binding 'foo)))
    (is (not (s/valid? ::lang/variable-binding 42)))
    (is (not (s/valid? ::lang/variable-binding "?str")))
    (is (not (s/valid? ::lang/variable-binding ':kw)))
    (is (not (s/valid? ::lang/variable-binding '[])))
    (is (not (s/valid? ::lang/variable-binding '[?e :kw 2])))
    (is (not (s/valid? ::lang/variable-binding '['?e :kw 2]))))
  (testing "S-expression"
    (is (s/valid? ::lang/s-expr '(= foo true)))
    (is (not (s/valid? ::lang/s-expr '[= foo true])))
    (is (not (s/valid? ::lang/s-expr '{:foo true})))
    (is (not (s/valid? ::lang/s-expr "foo"))))
  (testing "Fact binding"
    (is (s/valid? ::lang/fact-binding '[?foo <-]))
    (is (s/valid? ::lang/fact-binding '(?foo <-)))
    (is (not (s/valid? ::lang/fact-binding '(foo <-))))
    (is (not (s/valid? ::lang/fact-binding '(<- foo))))
    (is (not (s/valid? ::lang/fact-binding '(?foo)))))
  (testing "Test expr"
    (is (s/valid? ::lang/test-expr :test))
    (is (s/valid? ::lang/test-expr ':test)))
  (testing "Value equality"
    (is (s/valid? ::lang/value-equals-matcher :foo))
    (is (s/valid? ::lang/value-equals-matcher "foo"))
    (is (not (s/valid? ::lang/value-equals-matcher '("foo")))))
  (testing "Tuple-2"
    (is (s/valid? ::lang/tuple-2 '[?e :foo]))
    (is (s/valid? ::lang/tuple-2 '[1 :foo]))
    (is (s/valid? ::lang/tuple-2 '["x" :foo]))
    (is (not (s/valid? ::lang/tuple-2 '[?e "foo"])))
    (is (not (s/valid? ::lang/tuple-2 '[?e :foo "bar"]))))
  (testing "Tuple-3"
    (is (s/valid? ::lang/tuple-3 '[?e :foo "bar"]))
    (is (s/valid? ::lang/tuple-3 '[1 :foo "bar"]))
    (is (not (s/valid? ::lang/tuple-3 '[?e :foo])))
    (is (not (s/valid? ::lang/tuple-3 '[1 :foo])))
    (is (not (s/valid? ::lang/tuple-3 '["x" :foo])))
    (is (not (s/valid? ::lang/tuple-3 '[?e "foo"]))))
  (testing "Tuple-4"
    (is (s/valid? ::lang/tuple-4 '[?e :foo "bar" -1]))
    (is (s/valid? ::lang/tuple-4 '[?e :foo "bar" ?tx-id]))
    (is (s/valid? ::lang/tuple-4 '[_ :foo _ ?tx-id]))
    (is (not (s/valid? ::lang/tuple-4 '[?e :foo "bar" _])))
    (is (not (s/valid? ::lang/tuple-4 '[_ _ _ ?tx-id]))))
  (testing "Tuple"
    (is (s/valid? ::lang/tuple '[?e :foo]))
    (is (s/valid? ::lang/tuple '[?e :foo "bar"]))
    (is (s/valid? ::lang/tuple '[?e :foo "bar" -1]))
    (is (not (s/valid? ::lang/tuple "foo")))
    (is (not (s/valid? ::lang/tuple '(?e "foo" bar)))))
  (testing "Contains rule generator"
    (is (s/valid? ::lang/contains-rule-generator '(<- ?x (entities ?y))))))

;(gen/generate (s/gen ::lang/variable-binding))
;(gen/generate (s/gen ::lang/s-expr))
;(gen/generate (s/gen ::lang/test-expr))
;(gen/generate (s/gen ::lang/value-expr))
;(gen/generate (s/gen ::lang/accum-expr))
;(gen/generate (s/gen ::lang/tuple))

(run-tests)
