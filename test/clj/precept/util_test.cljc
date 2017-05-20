(ns precept.util-test
    (:require [clojure.test :refer [use-fixtures deftest testing is run-tests]]
              [precept.util :refer :all]
              [precept.core :as core]
              [precept.state :as state]
              [precept.query :as q]
              [precept.schema :as schema]
              [precept.tuplerules :refer [def-tuple-session]]
              [precept.schema-fixture :refer [test-schema]]
              [clara.tools.inspect :as inspect]
              [clara.tools.tracing :as trace]
              [clojure.spec :as s]
              [clara.rules :refer [query defquery fire-rules] :as cr]
              [clara.tools.tracing :as trace]
              [precept.util :as util])
    (:import [precept.util Tuple]))

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

(@state/ancestors-fn :test-attr/one-to-many)

(defn reset-globals [f]
  (reset! state/fact-index {})
  (make-ancestors-fn (schema/schema->hierarchy test-schema))
  (f))

(use-fixtures :each reset-globals)

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

(deftest Tuples->maps-test
  (let [tuples (conj (repeatedly 5 #(->Tuple 1 :test-attr/one-to-many 42 1))
                     (->Tuple 1 :test-attr/one-to-one "bar" 2)
                     (->Tuple 2 :test-attr/one-to-one "baz" 2))
        nested (->Tuple 1 :ents tuples 1)]
    (is (= (Tuples->maps tuples)
          [{:db/id 2 :test-attr/one-to-one "baz"}
           {:db/id 1 :test-attr/one-to-many '(42 42 42 42 42)
            :test-attr/one-to-one "bar"}]))
    (is (= (Tuples->maps nested)
           {:ents [{:db/id 2 :test-attr/one-to-one "baz"}
                   {:db/id 1
                    :test-attr/one-to-many '(42 42 42 42 42)
                    :test-attr/one-to-one "bar"}]
            :db/id 1}))))

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
    (let [_ (reset! state/fact-index {})
          session @(def-tuple-session mysess)
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
          facts [[-1 :foo "bar"]
                 [-1 :bar "baz"]
                 [-2 :baz "baz"]]
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

;; Note there appears to be no hierarchy within an ancestors list...We may
;; derive the hierarchy in an ordered fashon but that information appears to be
;; lost when converted to a set and supplying the :ancestors part of the hierarchy
;; to Clara. Not clear whether Clara knows everything is descended from :all.
;; May want to try conversion to a vector
(deftest ancestors-fn-test
  (let [h (schema/schema->hierarchy test-schema)
        ancestors-fn (util/make-ancestors-fn h)]
    (is (= #{:all :one-to-one :unique} (ancestors-fn :todo/title)))
    (is (= #{:all :one-to-one} (ancestors-fn :todo/done)))
    (is (= #{:all :one-to-one} (ancestors-fn :no-match)))
    (is (= #{:all :one-to-many} (ancestors-fn :todo/tags)))))

(deftest fact-index-key-test
  (let [no-match (->Tuple 1 :no-match 42 1)
        one-to-many (->Tuple 1 :todo/tags 42 2)
        one-to-one (->Tuple 1 :todo/done 43 3)
        unique (->Tuple 1 :todo/title "my unique title" 4)
        h (schema/schema->hierarchy test-schema)
        _ (make-ancestors-fn h)]
    (is (fn? @state/ancestors-fn))
    (is (= [:one-to-one 1 :todo/done] (fact-index-path one-to-one)))
    (is (= [:one-to-one 1 :no-match] (fact-index-path no-match)))
    (is (= [:one-to-many] (fact-index-path one-to-many)))
    (is (= [:unique :todo/title "my unique title"] (fact-index-path unique)))))

(deftest fact-indexing-test
  (let [h (schema/schema->hierarchy test-schema)
        _ (make-ancestors-fn h)
        fact-1 (->Tuple 1 :foo 42 1)
        fact-2 (->Tuple 1 :bar 42 2)
        next-1 (->Tuple 1 :foo 43 3)
        next-2 (->Tuple 1 :bar 43 4)
        one-to-many (->Tuple 1 :todo/tags 42 5)
        unique (->Tuple 1 :todo/title "my unique title" 6)]

    (testing "Initial state"
      (is (= {} (reset! state/fact-index {}))
          "Expected fact index to be {}")
      (is (fn? @state/ancestors-fn)
          "Expected ancestors-fn to be a fn"))

    (testing "Finding existing with empty state"
      (is (= nil (find-in-fact-index fact-1 (fact-index-path fact-1)))))

    (testing "Fact is indexed by find-existing"
      (is (= @state/fact-index {:one-to-one {1 {:foo fact-1}}})))

    (testing "Removing a fact that exists in index"
      (is (= true (remove-from-fact-index fact-1 (fact-index-path fact-1))))
      (is (= @state/fact-index {})))

    (testing "Newer one-to-one facts should return old fact"
      (is (= @state/fact-index {}))
      (is (= nil (find-in-fact-index fact-1 (fact-index-path fact-1))))
      (is (= fact-1 (find-in-fact-index next-1 (fact-index-path next-1)))))

    (testing "Existing one-to-one-fact should have been replaced"
      (is (= @state/fact-index {:one-to-one {1 {:foo next-1}}})))

    (testing "Remove fact from index that does not exist"
      (is (= false (remove-from-fact-index fact-1 (fact-index-path fact-1))))
      (is (= @state/fact-index {:one-to-one {1 {:foo next-1}}})))

    (testing "Find with same entity, different attribute should index the new fact and return
              nil (nothing to retract)"
      (is (= @state/fact-index {:one-to-one {1 {:foo next-1}}}))
      (is (= nil (find-in-fact-index fact-2 (fact-index-path fact-2))))
      (is (= @state/fact-index {:one-to-one {1 {:foo next-1
                                                :bar fact-2}}})))

    (testing "Removing same entity, same attribute but different fact-id should not alter index and
              return false to indicate nothing was removed"
      (is (= @state/fact-index {:one-to-one {1 {:foo next-1
                                                :bar fact-2}}}))
      (is (= false (remove-from-fact-index next-2 (fact-index-path next-2))))
      (is (= @state/fact-index {:one-to-one {1 {:foo next-1
                                                :bar fact-2}}})))

    (testing "One-to-many attrs do not affect fact index"
      (is (= @state/fact-index {:one-to-one {1 {:foo next-1
                                                :bar fact-2}}}))
      (is (= nil (find-in-fact-index one-to-many (fact-index-path one-to-many))))
      (is (= false (remove-from-fact-index one-to-many (fact-index-path one-to-many))))
      (is (= @state/fact-index {:one-to-one {1 {:foo next-1
                                                :bar fact-2}}})))

    (testing "Unique attrs should be indexed in own bucket"
      (is (= nil (find-in-fact-index unique (fact-index-path unique))))
      (is (= @state/fact-index {:one-to-one {1 {:foo next-1
                                                :bar fact-2}}
                                :unique {:todo/title {"my unique title" unique}}})))))

(run-tests)
