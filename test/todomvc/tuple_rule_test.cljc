(ns todomvc.tuple-rule-test
    [:require [clojure.test :refer [deftest testing is run-tests]]
              [clara.rules :refer [defrule]]
              [todomvc.tuple-rule :refer [binding?
                                          variable-bindings
                                          parse-as-tuple
                                          parse-with-fact-expression
                                          rewrite-lhs
                                          def-tuple-rule]]])

;TODO. generative testing

(deftest binding?-test
  (is (= true (binding? '?foo)))
  (is (= false (binding? 'foo)))
  (is (= false (binding? 42)))
  (is (= false (binding? "str")))
  (is (= false (binding? :kw)))
  (is (= false (binding? [])))
  (is (= false (binding? '[?e :kw 2])))
  (is (= false (binding? ['?e :kw 2]))))

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

(deftest value-or-sexprjj)

(deftest parse-as-tuple-test
  (is (= '[:ns/attr [[e a v]] (= ?e e)]
        (parse-as-tuple '[[?e :ns/attr _]])))
  (is (= '[:ns/attr [[e a v]] (= ?e e)]
        (parse-as-tuple '[[?e :ns/attr _]])))
  (is (= '[:ns/attr [[e a v]] (= ?e e) (= ?v v)]
        (parse-as-tuple '[[?e :ns/attr ?v]])))
  (is (= '[:ns/attr [[e a v]] (= ?e e)]
        (parse-as-tuple '[[?e :ns/attr]]))))

(deftest rewrite-lhs-test
  (testing "Ops - :exists, :not, :and, :or"
    (is (= '[[:exists [:ns/foo]]]
          (rewrite-lhs '[[:exists [:ns/foo]]]))
      "Should return exists keyword in first position as-is")
    (is (= '[[:not [:ns/foo]]]
          (rewrite-lhs '[[:not [:ns/foo]]]))))
  ;(is (= '[[:not [_ :ns/foo (= v 3)]]])
  ;    (rewrite-lhs '[[:not [:ns/foo]]]))))
  (testing "Should expand to clara equivalent"
    (is (= (macroexpand
             '(def-tuple-rule my-rule
                "Docstring!!"
                [[?e :todo/title _]]
                [:exists [:todo/done]]
                =>
                (println "Hello!")))
          (macroexpand
            '(defrule my-rule
               "Docstring!!"
               [:todo/title [[e a v]] (= ?e e)]
               [:exists [:todo/done]]
               =>
               (println "Hello!")))))))



(run-tests)

(macroexpand
  '(def-tuple-rule my-dsl-rule
     "Docstring!!"
     [?todo <- [?e :todo/title "Hi"]]
     ;[:exists [_ :todo/done (= ?v 3)]]
     [[_ :todo/done (= ?v 3)]]
     =>
     (println "Hello!")))


(macroexpand
  '(def-tuple-rule my-dsl-rule
     "Docstring!!"
     [[?e :todo/title]]
     [:exists [_ :todo/done (= ?v 3)]]
     =>
     (println "Hello!")))
;; bad
;; LHS out [[:todo/title [[e a v]] (= ?e e)] [:exists [_ :todo/done (= ?v 3)]]]

(macroexpand
  '(defrule my-rule
     "Docstring!!"
     [:todo/title [[e a v]] (= ?e e)]
     [:exists [:todo/done [[e a v]] (= ?v 3)]]
     =>
     (println "Hello!")))
