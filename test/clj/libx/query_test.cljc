(ns libx.query-test
  (:require [libx.util :refer :all]
            [libx.query :as q]
            [libx.tuplerules :refer [def-tuple-session def-tuple-rule]]
            [clojure.test :refer [testing is deftest run-tests]]
            [clara.rules :as cr]
            [clara.rules.accumulators :as acc])
  (:import [libx.util Tuple]))

(cr/defquery find-all [] [?all <- :all])
(cr/defquery find-all-acc [] [?all <- (acc/all) :from [:all]])

(deftest cr-query
  (testing "Clara query - single fact"
    (let [blank @(def-tuple-session default 'libx.query-test)
          s (-> blank (insert [-1 :attr "foo"])
                      (cr/fire-rules))
          start (:?all (first (cr/query blank find-all)))
          res (:?all (first (cr/query s find-all)))]
      (is (= start nil))
      (is (= res (Tuple. -1 :attr "foo")))))

  (testing "Clara query - many facts"
    (let [blank @(def-tuple-session default 'libx.query-test)
          to-insert [[-1 :attr "foo"]
                     [-2 :attr "bar"]
                     [-3 :attr "baz"]]
          s (-> blank (insert to-insert)
                      (cr/fire-rules))
          start (:?all (first (cr/query blank find-all-acc)))
          res (:?all (first (cr/query s find-all-acc)))]
      (is (= start []))
      (is (= res [(Tuple. -1 :attr "foo")
                  (Tuple. -2 :attr "bar")
                  (Tuple. -3 :attr "baz")]))))

  (testing "entities-where - many facts"
    (let [blank @(def-tuple-session default
                   'libx.query-test
                   'libx.query)
          to-insert [[-1 :first-name "Bob"]
                     [-1 :last-name "Smith"]
                     [-1 :email "bob@bobsmith.com"]
                     [-2 :first-name "Jim"]
                     [-2 :last-name "Brown"]
                     [-2 :email "jim@jimbrown.com"]]
          s (-> blank (insert to-insert)
              (cr/fire-rules))
          start (q/entities-where blank :first-name)
          res (q/entities-where s :first-name)]
      (is (= start '()))
      (is (= res [{:db/id -1
                   :first-name "Bob"
                   :last-name "Smith"
                   :email "bob@bobsmith.com"}
                  {:db/id -2
                   :first-name "Jim"
                   :last-name "Brown"
                   :email "jim@jimbrown.com"}])))))

(deftest entityv-test
  (let [s @(def-tuple-session entityv-session
                              'libx.query)
        inserted (-> s
                   (insert [[123 :foo "bar"]
                            [123 :foo "baz"]
                            [123 :bar "baz"]])
                   (cr/fire-rules))
        result (q/entityv inserted 123)]
    (is (= result [[123 :foo "bar"] [123 :foo "baz"] [123 :bar "baz"]]))))

;; TODO. This is wrong! Keys collide when creating map. Should use schema
;; to hydrate as one-to-many or provide warning
(deftest entity-test
  (let [s @(def-tuple-session entity-session
                              'libx.query)
        inserted (-> s
                   (insert [[123 :foo "bar"]
                            [123 :foo "baz"]
                            [123 :bar "baz"]])
                   (cr/fire-rules))
        result (q/entity inserted 123)]
    (is (= result {:db/id 123 :foo "baz" :bar "baz"}))))


(run-tests)
