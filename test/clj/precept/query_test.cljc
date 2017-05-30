(ns precept.query-test
    (:require [precept.util :refer :all :as util]
              [precept.query :as q]
              [precept.rules :refer [session rule]]
              [clojure.test :refer [use-fixtures testing is deftest run-tests]]
              [clara.rules :as cr]
              [clara.rules.accumulators :as acc]
              [precept.state :as state])
  (:import [precept.util Tuple]))

(defn reset-globals [f]
  (reset! state/fact-index {})
  (make-ancestors-fn)
  (f)
  (reset! state/fact-index {})
  (make-ancestors-fn))

(use-fixtures :each reset-globals)
(use-fixtures :once reset-globals)

(cr/defquery find-all [] [?all <- :all])
(cr/defquery find-all-acc [] [?all <- (acc/all) :from [:all]])

(deftest cr-query-single-fact
  (let [blank @(session default 'precept.query-test)
        s (-> blank (insert (->Tuple -1 :attr "foo" 500))
                    (cr/fire-rules))
        start (:?all (first (cr/query blank find-all)))
        res (:?all (first (cr/query s find-all)))]
    (is (= start nil))
    (is (= res (util/map->Tuple {:e -1 :a :attr :v "foo" :t 500})))))

(deftest cr-query-many-facts
  (let [blank @(session default 'precept.query-test)
        to-insert [(->Tuple -1 :attr "foo" 500)
                   (->Tuple -2 :attr "bar" 501)
                   (->Tuple -3 :attr "baz" 502)]
        s (-> blank (insert to-insert)
                    (cr/fire-rules))
        start (:?all (first (cr/query blank find-all-acc)))
        res (:?all (first (cr/query s find-all-acc)))]
    (is (= start []))
    (is (= (frequencies res)
           (frequencies [(util/map->Tuple {:e -1 :a :attr :v "foo" :t 500})
                         (util/map->Tuple {:e -2 :a :attr :v "bar" :t 501})
                         (util/map->Tuple {:e -3 :a :attr :v "baz" :t 502})])))))

(deftest entities-where-many-facts
  (let [blank @(session default
                 'precept.query-test
                 'precept.query)
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
    (is (= (frequencies
             '({:db/id -1
                :first-name "Bob"
                :last-name "Smith"
                :email "bob@bobsmith.com"}
               {:db/id -2
                :first-name "Jim"
                :last-name "Brown"
                :email "jim@jimbrown.com"}))
         (frequencies res)))))

(deftest entityv-test
  (let [s @(session entityv-session
                              'precept.query)
        inserted (-> s
                   (insert [[123 :foo "bar"]
                            [123 :foo-1 "baz"]
                            [123 :bar "baz"]])
                   (cr/fire-rules))
        result (q/entityv inserted 123)]
    (is (= (frequencies result)
           (frequencies [[123 :foo "bar"]
                         [123 :foo-1 "baz"]
                         [123 :bar "baz"]])))))


;; TODO. This is wrong! Keys will collide when creating map. Should use schema
;; to hydrate as one-to-many or provide warning
(deftest entity-test
  (let [s @(session entity-session
                              'precept.query)
        inserted (-> s
                   (insert [[123 :foo "bar"]
                            [123 :foo-2 "baz"]
                            [123 :bar "baz"]])
                   (cr/fire-rules))
        result (q/entity inserted 123)]
    (is (= result {:db/id 123 :foo "bar" :foo-2 "baz" :bar "baz"}))))


(run-tests)
