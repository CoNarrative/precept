(ns libx.util-test
    (:require [clojure.test :refer [deftest testing is run-tests]]
              [libx.util :refer :all]
              [libx.core :as core]
              [libx.state :as state]
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

(defn fact-id? [n]
  (and (> n -1) (<= n @state/fact-id)))

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
    (let [rtn (vec->record [-1 :attr "foo"])]
      (is (= Tuple (type rtn)))
      (is (= (butlast (vals rtn)) (list -1 :attr "foo")))
      (is (fact-id? (last (vals rtn))))))
  (testing "Tuple with no value in 3rd slot "
    (is (thrown-with-msg? clojure.lang.ExceptionInfo
            #"Received tuple without third slot"
          (vec->record [-1 :attr]))))
  (testing "Vector tuple with vector tuple in third slot"
    (let [rtn (vec->record [-1 :attr [-2 :nested "foo"]])]
      (is (record? rtn))
      (is (vector? (:v rtn)))
      (is (number? (:t rtn))))))
      ;(is (= (type rtn) Tuple))
      ;(is (= (type (:v rtn)) Tuple))
      ;(is 4 (count (vals rtn)))
      ;(is 4 (count (vals (:v rtn))))
      ;(is (fact-id? (:t rtn)))
      ;(is (fact-id? (:t (:v rtn)))))))

(deftest record->vec-test
  (testing "With single record no nesting"
    (is (= (record->vec (->Tuple -1 :attr "foo" -1))
           [-1 :attr "foo"])))
  (testing "Record with record in third slot"
    (is (= (record->vec (->Tuple -1 :attr (->Tuple -2 :nested "foo" -1) -1))
           [-1 :attr [-2 :nested "foo"]]))))

(deftest insertable-test
  (testing "Single vector"
    (let [rtn (insertable [-1 :attr "foo"])]
      (is (and (not (record? rtn)) (coll? rtn)))
      (is (every? #(= (type %) Tuple) rtn))))
  (testing "Vector of vectors"
    (let [rtn (insertable [[-1 :attr "foo"] [-1 :attr "bar"]])]
      (is (and (not (record? rtn)) (coll? rtn)))
      (is (every? #(= (type %) Tuple) rtn))))
  (testing "Single record"
   (is (= (insertable (->Tuple -1 :attr "foo" 123))
          [(->Tuple -1 :attr "foo" 123)])))
  (testing "List of records"
    (is (= (insertable (list (->Tuple -1 :attr "foo" -1) (->Tuple -1 :attr "bar" -1)))
           [(->Tuple -1 :attr "foo" -1) (->Tuple -1 :attr "bar" -1)])))
  (testing "Record inside vector tuple"
    (let [rtn (insertable [-1 :nested-v (->Tuple -1 :attr "foo" 111)])]
      (is (and (not (record? rtn)) (coll? rtn)))
      (is (every? #(= (type %) Tuple) rtn))
      (is (= (->Tuple -1 :attr "foo" 111)
             (:v (first rtn))))))
  (testing "Records inside vector tuples"
    (let [rtn (insertable [[-1 :nested-v (->Tuple -1 :attr "foo" 111)]
                           [-2 :nested-v (->Tuple -2 :attr "foo" 222)]])]
      (is (and (not (record? rtn)) (coll? rtn)))
      (is (every? #(= (type %) Tuple) rtn))
      (is (= (->Tuple -1 :attr "foo" 111)
             (:v (first rtn))))
      (is (= (->Tuple -2 :attr "foo" 222)
             (:v (second rtn))))))
  (testing "List of vectors"
    (let [rtn (insertable (list [123 :sub/request :todo-app]))]
      (is (vector? rtn))
      (is (every? #(= (type %) Tuple) rtn))))
  (testing "Vector with a vector as data in :v position"
    (let [rtn (insertable [123 :vector-as-data [1 :foo]])]
      (is (vector? rtn))
      (is (every? #(= Tuple (type %)) rtn))
      (is (every? #(number? (:t %)) rtn))
      (is (every? #(vector? (:v %)) rtn))
      (is (= [1 :foo] (:v (first rtn))))))
  (testing "Vector with hash-map as data in :v position"
    (let [rtn (insertable [123 :vector-as-data {:a 1}])]
      (is (vector? rtn))
      (is (every? #(= Tuple (type %)) rtn))
      (is (every? #(number? (:t %)) rtn))
      (is (every? #(map? (:v %)) rtn))
      (is (= {:a 1} (:v (first rtn)))))))

(deftest insert-test
  (testing "Insert single tuple"
    (let [session @(def-tuple-session mysess)
          fact [-1 :foo "bar"]
          trace (trace/get-trace (-> session
                                   (trace/with-tracing)
                                   (insert fact)
                                   (fire-rules)))]
      (is (= :add-facts (:type (first trace))))
      (is (every? #(= Tuple (type %)) (:facts (first trace)))
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
      (is (vector? (:facts (first trace))))
      (is (every? #(= Tuple (type %)) (:facts (first trace)))
          "Each inserted fact should be a Tuple"))))

  ;; TODO. Reinstate
  ;(testing "Insert single map"
  ;  (let [session @(def-tuple-session mysess)
  ;        fact-m  (todo-tx (guid) "Hi" :tag)
  ;        trace (trace/get-trace (-> session
  ;                                 (trace/with-tracing)
  ;                                 (insert fact-m)
  ;                                 (fire-rules)))]
  ;    (is (= :add-facts (:type (first trace)))))))
  ;    (is (= (map #(apply ->Tuple %) (map->tuples fact-m)) (:facts (first trace)))
  ;        "Fact map should have been inserted as Tuples"))))
  ;(testing "Insert vector of maps"
  ;  (let [session @(def-tuple-session mysess)
  ;        numfacts 5
  ;        m-fact  #(todo-tx (java.util.UUID/randomUUID) "Hi" :tag)
  ;        m-facts (into [] (repeatedly numfacts m-fact))
  ;        trace (trace/get-trace (-> session
  ;                                (trace/with-tracing)
  ;                                (insert m-facts)
  ;                                (fire-rules)))]
  ;    (is (= :add-facts (:type (first trace))))
  ;    (is (= (count (:facts (first trace)))
  ;           (* numfacts (dec (count (keys (m-fact))))))))))

(deftest gen-Tuples-from-map-test
  (let [m {:first-name "Bob" :last-name "Smith"}
        rtn (gen-Tuples-from-map m)]
    (is (vector? rtn))
    (is (every? #(= (type %) Tuple) rtn))
    (is (every? #(= % [:e :a :v :t]) (mapv keys rtn)))))

(deftest activation-group-test
  (testing "Returning booleans from properties based on list order"
    (let [group-fn (make-activation-group-fn core/default-group)
          sort-fn (make-activation-group-sort-fn core/groups core/default-group)
          first-group-props {:props {:group (first core/groups)}}
          last-group-props {:props {:group (last core/groups)}}
          first-group-result (group-fn first-group-props)
          last-group-result (group-fn last-group-props)]
      (is (= {:group (first core/groups) :salience 0 :super nil}
             first-group-result))
      (is (= {:group (last core/groups) :salience 0 :super nil}
             last-group-result))
      (is (= true (sort-fn first-group-result last-group-result)))
      (is (= false (sort-fn last-group-result first-group-result))))))


(run-tests)
