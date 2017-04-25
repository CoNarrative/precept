(ns libx.util-test
    (:require [clojure.test :refer [deftest testing is run-tests]]
              [libx.util :refer :all]
              [libx.query :as q]
              [libx.tuplerules :refer [def-tuple-session]]
              [clara.tools.inspect :as inspect]
              [clara.tools.tracing :as trace]
              [clojure.spec :as s]
              [clara.rules :refer [query defquery fire-rules] :as cr]
              [clara.tools.tracing :as trace])
    (:import [libx.util Tuple]))

(defn todo-tx [id title done]
  (merge
    {:db/id      id
     :todo/title title}
    (when-not (nil? done)
      {:todo/done done})))

(defn is-tuple? [x]
  (and (vector x)
    (= (count x) 3)
    (keyword? (second x))))

(deftest map->tuples-test
  (testing "Converting an entity map to a vector of tuples"
    (let [entity-map (todo-tx (java.util.UUID/randomUUID) "Hi" :tag)
          entity-vec (map->tuples entity-map)]
      (is (map? entity-map)
        "Should receive a hashmap as input")
      (is (vector? entity-vec)
        "Should return a vector")
      (is (every? is-tuple? entity-vec)
        "Every entry in main vector should pass the tuple test")
      (is (every? #(= (first %) (:db/id entity-map)) entity-vec)
        "Every entry should use the :db/id from the map for its eid"))))

(deftest tuplize-test
  (let [m-fact  (todo-tx (java.util.UUID/randomUUID) "Hi" :tag)
        m-facts (into [] (repeat 5 m-fact))]
    (is (every? is-tuple? (tuplize-into-vec m-fact)))
    (is (every? is-tuple? (tuplize-into-vec m-facts)))
    (is (every? is-tuple? (tuplize-into-vec (tuplize-into-vec m-fact))))
    (is (every? is-tuple? (tuplize-into-vec (tuplize-into-vec m-facts))))))

(deftest vec->record-test
  (testing "Tuple no nesting"
    (is (= (vec->record [-1 :attr "foo"])
           (->Tuple -1 :attr "foo" -1))))
  (testing "Tuple with no value in 3rd slot "
    (is (thrown-with-msg? clojure.lang.ExceptionInfo
            #"Received tuple without third slot"
          (vec->record [-1 :attr]))))
  (testing "Tuple with tuple in third slot"
    (is (= (vec->record [-1 :attr [-2 :nested "foo"]])
           (->Tuple -1 :attr (->Tuple -2 :nested "foo" -1) -1)))))

(deftest record->vec-test
  (testing "With single record no nesting"
    (is (= (record->vec (->Tuple -1 :attr "foo" -1))
           [-1 :attr "foo"])))
  (testing "Record with record in third slot"
    (is (= (record->vec (->Tuple -1 :attr (->Tuple -2 :nested "foo" -1) -1))
           [-1 :attr [-2 :nested "foo"]]))))

(deftest insertable-test
  (testing "Single vector"
    (is (= (insertable [-1 :attr "foo"]) [(->Tuple -1 :attr "foo" -1)])))
  (testing "Vector of vectors"
    (is (= (insertable [[-1 :attr "foo"] [-1 :attr "bar"]])
          [(->Tuple -1 :attr "foo" -1)
           (->Tuple -1 :attr "bar" -1)])))
  (testing "Single record"
   (is (= (insertable (->Tuple -1 :attr "foo" -1))
          [(->Tuple -1 :attr "foo" -1)])))
  (testing "List of records"
    (is (= (insertable (list (->Tuple -1 :attr "foo" -1) (->Tuple -1 :attr "bar" -1)))
           [(->Tuple -1 :attr "foo" -1) (->Tuple -1 :attr "bar" -1)])))
  (testing "Record inside tuple"
    (is (= (insertable [-1 :nested-v (->Tuple -1 :attr "foo" -1)])
           [(->Tuple -1 :nested-v (->Tuple -1 :attr "foo" -1) -1)])))
  (testing "Records inside tuples"
    (is (= (insertable [[-1 :nested-v (->Tuple -1 :attr "foo" -1)]
                        [-2 :nested-v (->Tuple -2 :attr "foo" -1)]])
          [(->Tuple -1 :nested-v (->Tuple -1 :attr "foo" -1) -1)
           (->Tuple -2 :nested-v (->Tuple -2 :attr "foo" -1) -1)]))))

(deftest insert-test
  (testing "Insert single tuple"
    (let [session @(def-tuple-session mysess)
          fact [-1 :foo "bar"]
          trace (trace/get-trace (-> session
                                   (trace/with-tracing)
                                   (insert fact)
                                   (fire-rules)))]
      (is (= :add-facts (:type (first trace))))
      (is (= (list (->Tuple -1 :foo "bar" -1)) (:facts (first trace)))
        "Inserted fact should be a Tuple")))

  (testing "Insert tuples"
    (let [session @(def-tuple-session mysess)
          facts [[-1 :foo "bar" -1]
                 [-1 :bar "baz" -1]
                 [-2 :foo "baz" -1]]
          trace (trace/get-trace (-> session
                                   (trace/with-tracing)
                                   (insert facts)
                                   (fire-rules)))]
      (is (= :add-facts (:type (first trace))))
      (is (= (map #(apply ->Tuple %) facts) (:facts (first trace)))
          "Each inserted fact should be a Tuple")))

  (testing "Insert single map"
    (let [session @(def-tuple-session mysess)
          fact-m  (todo-tx (guid) "Hi" :tag)
          trace (trace/get-trace (-> session
                                   (trace/with-tracing)
                                   (insert fact-m)
                                   (fire-rules)))]
      (is (= :add-facts (:type (first trace))))))
      ;TODO.
      ;(is (= (map #(apply ->Tuple %) (map->tuples fact-m)) (:facts (first trace)))
      ;    "Fact map should have been inserted as Tuples")))

  (testing "Insert vector of maps"
    (let [session @(def-tuple-session mysess)
          numfacts 5
          m-fact  #(todo-tx (java.util.UUID/randomUUID) "Hi" :tag)
          m-facts (into [] (repeatedly numfacts m-fact))
          trace (trace/get-trace (-> session
                                  (trace/with-tracing)
                                  (insert m-facts)
                                  (fire-rules)))]
      (is (= :add-facts (:type (first trace))))
      (is (= (count (:facts (first trace)))
             (* numfacts (dec (count (keys (m-fact)))))))))

  (testing "RHS insert (insert!)"
    (is (= (list (->Tuple -1 :attr "foo" -1))
           (map vec->record (mapcat tuplize-into-vec (list [-1 :attr "foo"])))))
    (is (= (list (->Tuple -1 :attr "foo" -1) (->Tuple -1 :attr "bar" -1))
           (map vec->record (mapcat tuplize-into-vec (list [[-1 :attr "foo"] [-1 :attr "bar"]])))))
    (is (= (list (->Tuple -1 :nested-v (->Tuple -1 :attr "bar" -1) -1))
           (map vec->record (mapcat tuplize-into-vec
                              (list [-1 :nested-v (->Tuple -1 :attr "bar" -1)])))))))


(run-tests)
