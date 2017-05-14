(ns precept.tuple-rule-test
    (:require [clojure.test :refer [deftest testing is run-tests]]
              [clara.rules :refer [defrule defquery]]
              [clara.rules.accumulators :as acc]
              [precept.spec.sub :as sub]
              [precept.tuplerules :refer [def-tuple-rule
                                          def-tuple-query
                                          defsub
                                          store-action]]
              [precept.util :refer [insert!] :as util]
              [precept.macros :refer [binding?
                                      variable-bindings
                                      sexprs-with-bindings
                                      positional-value
                                      value-expr?
                                      parse-as-tuple
                                      parse-with-fact-expression
                                      rewrite-lhs
                                      <- entity]]))

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
        (parse-as-tuple '[[?e :ns/attr]])))
  (is (= '[:ns/attr (= ?tx-id (:t this))]
        (parse-as-tuple '[[_ :ns/attr _ ?tx-id]])))
  (is (= '[:ns/attr (= -1 (:t this))]
        (parse-as-tuple '[[_ :ns/attr _ -1]]))))

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
                (insert! "RHS")))
          (macroexpand
            '(defrule my-rule
               [:todo/title (= ?e (:e this))]
               [:exists [:todo/done]]
               =>
               (insert! "RHS"))))))
  (testing "Match on a value"
    (is (= (macroexpand
             '(def-tuple-rule my-rule
                [[?e :todo/title "Hello"]]
                =>
                (insert! "RHS")))
          (macroexpand
            '(defrule my-rule
               [:todo/title (= ?e (:e this)) (= "Hello" (:v this))]
               =>
               (insert! "RHS"))))))
  (testing "With accumulator, fact-type only"
    (is (= (macroexpand
             '(def-tuple-rule my-rule
                [?foo <- (acc/all) from [:todo/title]]
                =>
                (insert! "RHS")))
           (macroexpand
             '(defrule my-rule
                [?foo <- (acc/all) from [:todo/title]]
                =>
                (insert! "RHS"))))))
  (testing "With op that contains tuple expression"
    (is (= (macroexpand
            '(def-tuple-rule my-rule
              [:not [?e :todo/done]]
              =>
              (insert! "RHS")))
          (macroexpand
           '(defrule my-rule
             [:not [:todo/done (= ?e (:e this))]]
             =>
             (insert! "RHS"))))))
  (testing "With nested ops"
      (is (= (macroexpand
              '(def-tuple-rule my-rule
                 [:not [:exists [:todo/done]]]
                 =>
                 (insert! "RHS")))
            (macroexpand
              '(defrule my-rule
                [:not [:exists [:todo/done]]]
                =>
                (insert! "RHS"))))))
  (testing "Accum with fact assignment"
    (is (= (macroexpand
             '(def-tuple-rule my-rule
                [?entity <- (acc/all) :from [?e :all]]
                =>
                (insert! "RHS")))
          (macroexpand
            '(defrule my-rule
               [?entity <- (acc/all) :from [:all (= ?e (:e this))]]
               =>
               (insert! "RHS"))))))
  (testing "Fact assignment with brackets around fact-type"
    (is (= (macroexpand
             '(def-tuple-rule my-rule
                [?entity <- [:my-attribute]]
                =>
                (insert! "RHS")))
          (macroexpand
            '(defrule my-rule
               [?entity <- :my-attribute]
               =>
               (insert! "RHS"))))))
  (testing "Fact assignment no brackets around fact-type"
    (is (= (macroexpand
             '(def-tuple-rule my-rule
                [?entity <- :my-attribute]
                =>
                (insert! "RHS")))
          (macroexpand
            '(defrule my-rule
               [?entity <- :my-attribute]
               =>
               (insert! "RHS"))))))
  (testing "Tx-id field - bind"
    (is (= (macroexpand
             '(def-tuple-rule my-rule
                [[_ :my-attribute _ ?t]]
                =>
                (insert! "RHS")))
          (macroexpand
            '(defrule my-rule
               [:my-attribute (= ?t (:t this))]
               =>
               (insert! "RHS"))))))
  (testing "Tx-id field - bind the fact"
    (is (= (macroexpand
             '(def-tuple-rule my-rule
                [?fact <- [_ :my-attribute _ ?t]]
                =>
                (insert! "RHS")))
          (macroexpand
            '(defrule my-rule
               [?fact <- :my-attribute (= ?t (:t this))]
               =>
               (insert! "RHS"))))))
  (testing "With :test op"
    (is (= (macroexpand
             '(def-tuple-rule my-rule
                [?fact <- [_ :my-attribute _ ?t]]
                [:test (> ?t ?fact)]
                =>
                (insert! "RHS")))
          (macroexpand
            '(defrule my-rule
               [?fact <- :my-attribute (= ?t (:t this))]
               [:test (> ?t ?fact)]
               =>
               (insert! "RHS"))))))
  (testing "Bind to value of s-expr"
    (is (= (macroexpand
             '(def-tuple-rule my-rule
                [[_ :my-attribute ?v]]
                [[(:id ?v) :foo]]
                =>
                (insert! "RHS")))
          (macroexpand
            '(defrule my-rule
               [:my-attribute (= ?v (:v this))]
               [:foo (= (:id ?v) (:e this))]
               =>
               (insert! "RHS"))))))
  (testing "Value match in e position with fact binding"
    (is (= (macroexpand
             '(def-tuple-rule action-cleanup-2
                {:group :cleanup}
                [?fact <- [:this-tick :all ?v]]
                =>
                (trace "CLEANING this-tick fact because transient" ?fact)
                (cr/retract! ?fact)))
          (macroexpand
            '(defrule action-cleanup-2
               {:group :cleanup}
               [?fact <- :all (= ?v (:v this)) (= :this-tick (:e this))]
               =>
               (trace "CLEANING this-tick fact because transient" ?fact)
               (cr/retract! ?fact)))))))

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

(deftest store-action-test
  (testing "Expansion should be the same as equivalent defrule"
    (is (= (macroexpand
             '(store-action :foo))
           (macroexpand
             '(defrule action-handler-foo
                {:group :action}
                [:foo (= ?v (:v this))]
                =>
                (precept.util/action-insert! ?v)))))))

(deftest special-form-test
  (is (= (macroexpand
           '(def-tuple-rule my-rule
              [[?e :some-attr]]
              [(<- ?the-ent (entity ?e))]
              =>
              (insert! "RHS")))
        (macroexpand
          '(defrule my-rule
             [:some-attr (= ?e (:e this))]
             [?the-ent <- (clara.rules.accumulators/all) :from [:all (= ?e (:e this))]]
             =>
             (insert! "RHS"))))))

(deftest defsub-test
  (is (= (macroexpand
           '(defsub :task-list
              [[_ :visible-todos-list ?visible-todos]]
              [[_ :active-count ?active-count]]
              =>
              {:visible-todos ?visible-todos
               :all-complete? (= ?active-count 0)}))
         (macroexpand
            '(defrule task-list-sub___impl
              {:group :report}
              [::sub/request (= ?e (:e this)) (= :task-list (:v this))]
              [:visible-todos-list (= ?visible-todos (:v this))]
              [:active-count (= ?active-count (:v this))]
              =>
              (precept.util/insert!
                [?e ::sub/response {:all-complete? (= ?active-count 0)
                                    :visible-todos ?visible-todos}]))))))



(run-tests)

