(ns todomvc.util-test
  (:require [clojure.test :refer [deftest testing is run-tests]]
            [todomvc.tuplerules :refer [def-tuple-session]]
            [todomvc.util :refer :all]
            [clara.rules :refer [query defquery]]))

(defquery find-all []
  [:all [[e a v]] (= ?e e) (= ?a a) (= ?v v)])

(defn todo-tx [id title done]
  (merge
    {:db/id        id
     :todo/title   title}
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

(deftest insertable-test
  (let [fact (todo-tx (java.util.UUID/randomUUID) "Hi" :tag)
        facts (into [] (repeat 5 fact))]
    (is (= (every? is-tuple? (insertable fact))))
    (is (= (every? is-tuple? (insertable facts))))
    (is (= (every? is-tuple? (insertable (insertable fact)))))
    (is (= (every? is-tuple? (insertable (insertable facts)))))))


(run-tests)
