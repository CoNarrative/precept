(ns todomvc.tuple-rule-test
    [:require [clojure.test :refer [deftest testing is run-tests]]
              [todomvc.tuple-rule :refer [binding?
                                          tuple-bindings
                                          parse-as-tuple
                                          parse-with-fact-expression
                                          rewrite-lhs]]])

;TODO. generative testing

(deftest binding?-test
  (is (= true (binding? '?foo)))
  (is (= false (binding? 'foo)))
  (is (= false (binding? 42)))
  (is (= false (binding? :kw)))
  (is (= false (binding? [])))
  (is (= false (binding? '[?e :kw 2])))
  (is (= false (binding? ['?e :kw 2]))))

(deftest tuple-bindings-test
  (let [e     '[?e :ns/foo 42]
        e-a   '[?e ?a 42]
        e-a-v '[?e ?a ?v]
        _-a-v '[_ ?a ?v]
        _-a-v '[_ ?a ?v]]
    (is (= {:e '?e} (tuple-bindings e)))
    (is (= {:e '?e :a '?a} (tuple-bindings e-a)))
    (is (= {:e '?e :a '?a :v '?v} (tuple-bindings e-a-v)))
    (is (= {:a '?a :v '?v} (tuple-bindings _-a-v)))))

(deftest parse-as-tuple-test
  (is (= '[:ns/attr [[e a v]] (= ?e e)]
          (parse-as-tuple '[[?e :ns/attr _]])))
  (is (= '[:ns/attr [[e a v]] (= ?e e)]
          (parse-as-tuple '[[?e :ns/attr _]])))
  (is (= '[:ns/attr [[e a v]] (= ?e e) (= ?v v)]
          (parse-as-tuple '[[?e :ns/attr ?v]]))))

(deftest rewrite-lhs-test
  (testing "Any form with an op in first position gets returned"
    (is (= '[[:exists [:ns/foo]]]
           (rewrite-lhs '[[:exists [:ns/foo]]])))
    (is (= '[[:not [:ns/foo]]])
        (rewrite-lhs '[[:not [:ns/foo]]]))))

(run-tests)
