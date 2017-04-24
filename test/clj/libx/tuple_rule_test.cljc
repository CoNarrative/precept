(ns libx.tuple-rule-test
    (:require [clojure.test :refer [deftest testing is run-tests]]
              [clara.rules :refer [defrule defquery]]
              [clara.rules.accumulators :as acc]
              [libx.tuplerules :refer [def-tuple-rule def-tuple-query]]
              [libx.macros :refer [binding?
                                   variable-bindings
                                   positional-value
                                   value-expr?
                                   parse-as-tuple
                                   parse-with-fact-expression
                                   rewrite-lhs]]))

(deftest tuple-bindings-test
  (let [e1    '[?e :ns/foo 42]
        e2    '[?e :ns/foo]
        e3    '[?e :ns/foo _]
        e-a   '[?e ?a 42]
        e-a-v '[?e ?a ?v]
        _-a-v '[_ ?a ?v]]
    (is (= {:e '?e} (variable-bindings e1)))
    (is (= {:e '?e} (variable-bindings e2)))
    (is (= {:e '?e} (variable-bindings e3)))
    (is (= {:e '?e :a '?a} (variable-bindings e-a)))
    (is (= {:e '?e :a '?a :v '?v} (variable-bindings e-a-v)))
    (is (= {:a '?a :v '?v} (variable-bindings _-a-v)))))

(deftest positional-value-test
  (let [e1    '[?e :ns/foo 42]
        e2    '[?e :ns/foo]
        e3    '[?e :ns/foo _]
        e-a   '[?e ?a 42]
        e-a-v '[?e ?a ?v]
        _-a-v '[_ ?a ?v]]
    (is (= {:v '(= 42 (:v this))} (positional-value e1)))
    (is (= {} (positional-value e2)))
    (is (= {} (positional-value e3)))
    (is (= {:v '(= 42 (:v this))} (positional-value e-a)))
    (is (= {} (positional-value e-a-v)))
    (is (= {} (positional-value _-a-v)))))

(deftest parse-as-tuple-test
  (is (= '[:ns/attr (= ?e (:e this))]
        (parse-as-tuple '[[?e :ns/attr _]])))
  (is (= '[:ns/attr (= ?e (:e this))]
        (parse-as-tuple '[[?e :ns/attr _]])))
  (is (= '[:ns/attr (= ?e (:e this)) (= ?v (:v this))]
        (parse-as-tuple '[[?e :ns/attr ?v]])))
  (is (= '[:ns/attr (= ?e (:e this))]
        (parse-as-tuple '[[?e :ns/attr]]))))

(deftest rewrite-lhs-test
  (testing "Ops - :exists, :not, :and, :or"
    (is (= '[[:exists [:ns/foo]]]
          (rewrite-lhs '[[:exists [:ns/foo]]]))
      "Should return exists keyword in first position as-is")
    (is (= '[[:not [:ns/foo]]]
          (rewrite-lhs '[[:not [:ns/foo]]]))))
  (testing ":test keyword should pass through"
    (is (= '[[:test (= 1 1)]]
           (rewrite-lhs '[[:test (= 1 1)]]))))
  (testing "Fact assignment: Attribute-only with no brackets"
    (is (= '[[?toggle <- :ui/toggle-complete]]
          (rewrite-lhs '[[?toggle <- :ui/toggle-complete]]))))
  (testing "Fact assignment: Attribute-only with brackets"
    (is (= '[[?toggle <- :ui/toggle-complete]]
          (rewrite-lhs '[[?toggle <- [:ui/toggle-complete]]])))
    (is (= '[[:not [:ns/foo (= 3 (:v this))]]]
           (rewrite-lhs '[[:not [_ :ns/foo 3]]]))))
  (testing "Basic rule with exists"
    (is (= (macroexpand
             '(def-tuple-rule my-rule
                [[?e :todo/title _]]
                [:exists [:todo/done]]
                =>
                (println "RHS")))
          (macroexpand
            '(defrule my-rule
               [:todo/title (= ?e (:e this))]
               [:exists [:todo/done]]
               =>
               (println "RHS"))))))
  (testing "Match on a value"
    (is (= (macroexpand
             '(def-tuple-rule my-rule
                [[?e :todo/title "Hello"]]
                =>
                (println "RHS")))
          (macroexpand
            '(defrule my-rule
               [:todo/title (= ?e (:e this)) (= "Hello" (:v this))]
               =>
               (println "RHS"))))))
  (testing "With accumulator, fact-type only"
    (is (= (macroexpand
             '(def-tuple-rule my-rule
                [?foo <- (acc/all) from [:todo/title]]
                =>
                (println "RHS")))
           (macroexpand
             '(defrule my-rule
                [?foo <- (acc/all) from [:todo/title]]
                =>
                (println "RHS"))))))
  (testing "With op that contains tuple expression"
    (is (= (macroexpand
            '(def-tuple-rule my-rule
              [:not [?e :todo/done]]
              =>
              (println "RHS")))
          (macroexpand
           '(defrule my-rule
             [:not [:todo/done (= ?e (:e this))]]
             =>
             (println "RHS"))))))
  (testing "With nested ops"
      (is (= (macroexpand
              '(def-tuple-rule my-rule
                 [:not [:exists [:todo/done]]]
                 =>
                 (println "RHS")))
            (macroexpand
              '(defrule my-rule
                [:not [:exists [:todo/done]]]
                =>
                (println "RHS"))))))
  (testing "Accum with fact assignment"
    (is (= (macroexpand
             '(def-tuple-rule my-rule
                [?entity <- (acc/all) :from [?e :all]]
                =>
                (println "RHS")))
          (macroexpand
            '(defrule my-rule
               [?entity <- (acc/all) :from [:all (= ?e (:e this))]]
               =>
               (println "RHS"))))))
  (testing "Fact assignment with brackets around fact-type"
    (is (= (macroexpand
             '(def-tuple-rule my-rule
                [?entity <- [:my-attribute]]
                =>
                (println "RHS")))
          (macroexpand
            '(defrule my-rule
               [?entity <- :my-attribute]
               =>
               (println "RHS"))))))
  (testing "Fact assignment no brackets around fact-type"
    (is (= (macroexpand
             '(def-tuple-rule my-rule
                [?entity <- :my-attribute]
                =>
                (println "RHS")))
          (macroexpand
            '(defrule my-rule
               [?entity <- :my-attribute]
               =>
               (println "RHS")))))))

(deftest def-tuple-query-test
  (testing "Query with no args"
    (is (= (macroexpand
             '(def-tuple-query my-query []
                [[?e :foo ?v]]))
           (macroexpand
             '(defquery my-query []
                [:foo (= ?e (:e this)) (= ?v (:v this))])))))
  (testing "Query with args"
    (is (= (macroexpand
             '(def-tuple-query my-query [:?e]
                [[?e :foo ?v]]))
          (macroexpand
            '(defquery my-query [:?e]
               [:foo (= ?e (:e this)) (= ?v (:v this))]))))))


(run-tests)

