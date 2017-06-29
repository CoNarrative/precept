(ns precept.util-test
    (:require [clojure.test :refer [use-fixtures deftest testing is run-tests]]
              [precept.util :refer :all]
              [precept.core :as core]
              [precept.state :as state]
              [precept.schema :as schema]
              [precept.rules :refer [session]]
              [precept.schema-fixture :refer [test-schema]]
              [clara.tools.inspect :as inspect]
              [clara.tools.tracing :as trace]
              [clojure.spec :as s]
              [precept.spec.test :as test]
              [precept.spec.error :as err]
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

(deftest any-Tuple?-test
  (let [tuples (conj (repeatedly 5 #(->Tuple 1 ::test/one-to-many 42 1))
                 (->Tuple 2 ::test/one-to-one "bar" 2)
                 (->Tuple 3 ::test/one-to-one "baz" 2))
        nested (->Tuple 1 :ents tuples 1)
        entity-tuples (list (vec (conj (repeatedly 5 #(->Tuple 1 ::test/one-to-many 42 1))
                                   (->Tuple 1 ::test/one-to-one 42 1)))
                            (vec (conj (repeatedly 5 #(->Tuple 2 ::test/one-to-many 42 1))
                                   (->Tuple 2 ::test/one-to-one 42 1))))]
    (is (= true (any-Tuple? tuples)))
    (is (= true (any-Tuple? entity-tuples)))
    (is (= true (any-Tuple? nested)))))

(deftest Tuples->maps-test
  (let [tuples (conj (repeatedly 5 #(->Tuple 1 ::test/one-to-many 42 1))
                     (->Tuple 1 ::test/one-to-one "bar" 2)
                     (->Tuple 2 ::test/one-to-one "baz" 2))
        nested (->Tuple 1 :ents tuples 1)
        entity-tuples (list (vec (conj (repeatedly 5 #(->Tuple 1 ::test/one-to-many 42 1))
                                   (->Tuple 1 ::test/one-to-one 42 1)))
                            (vec (conj (repeatedly 5 #(->Tuple 2 ::test/one-to-many 42 1))
                                   (->Tuple 2 ::test/one-to-one 42 1))))]
    (is (= (Tuples->maps tuples)
          [{:db/id 2 ::test/one-to-one "baz"}
           {:db/id 1 ::test/one-to-many '(42 42 42 42 42)
            ::test/one-to-one "bar"}]))
    (is (= (Tuples->maps nested)
           {:ents [{:db/id 2 ::test/one-to-one "baz"}
                   {:db/id 1
                    ::test/one-to-many '(42 42 42 42 42)
                    ::test/one-to-one "bar"}]
            :db/id 1}))
    (is (= (Tuples->maps entity-tuples)
           [{:db/id 1 ::test/one-to-many '(42 42 42 42 42) ::test/one-to-one 42}
            {:db/id 2 ::test/one-to-many '(42 42 42 42 42) ::test/one-to-one 42}]))))

(deftest entity-map->Tuples-test
  (let [m {:db/id 123 ::test/one-to-one "foo" ::test/one-to-many [42 42 42 42 42]}
        records (entity-map->Tuples m)]
    (is (every? #(= Tuple (type %)) records))
    (is (= 1 (count (filter #(= ::test/one-to-one (:a %)) records))))
    (is (= 5 (count (filter #(= ::test/one-to-many (:a %)) records))))))

(deftest insertable-test
  (testing "Single vector"
    (let [rtn (insertable [-1 :attr nil])]
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
      (is (= {:a 1} (:v (first rtn))))))
  (testing "Entity map"
    (let [rtn (insertable {:db/id 123 :foo "bar" :baz "quux"})]
      (is (vector? rtn))
      (is (every? #(= Tuple (type %)) rtn))
      (is (every? #(number? (:t %)) rtn))))
  (testing "Entity maps"
    (let [rtn (insertable [{:db/id 123 :foo "bar" :baz "quux"}
                           {:db/id 234 :bar "foo" :quux "baz"}])]
      (is (vector? rtn))
      (is (every? #(= Tuple (type %)) rtn))
      (is (every? #(number? (:t %)) rtn)))))


(deftest insert-test
  (testing "Insert single tuple"
    (let [_ (reset! state/fact-index {})
          session @(session mysess)
          fact [-1 :foo "bar"]
          trace (trace/get-trace (-> session
                                   (trace/with-tracing)
                                   (insert fact)
                                   (fire-rules)))]
      (is (= :add-facts (:type (first trace))))
      (is (every? #(= Tuple (type %)) (:facts (first trace)))
        "Inserted fact should be a Tuple")))

  (testing "Insert tuples"
    (let [session @(session mysess)
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

(deftest conform-insertions-and-retractions!-test
  (let [facts [[-1 ::test/one-to-one "foo"]
               [-1 ::test/unique-identity "foo"]
               [-2 ::test/unique-identity "foo"]]
        [to-insert to-retract] (conform-insertions-and-retractions! facts)]
    (is (= (frequencies (mapv :a to-insert))
           (frequencies [::err/type ::err/existing-fact ::err/failed-insert
                         ::test/unique-identity ::test/one-to-one])))
    (is (= (mapv :a to-retract) [])))

  (testing "Insert entity map"
    (let [session @(session mysess)
          fact-m (todo-tx (guid) "Hi" :tag)
          trace (trace/get-trace (-> session
                                   (trace/with-tracing)
                                   (insert fact-m)
                                   (fire-rules)))]
      (is (= :add-facts (:type (first trace))))
      (is (every? #(= Tuple (type %)) (:facts (first trace)))
          "Entity map should have been inserted as Tuples")))

  (testing "Insert entity maps"
    (let [session @(session mysess)
          numfacts 5
          m-fact #(todo-tx (java.util.UUID/randomUUID) "Hi" :tag)
          m-facts (into [] (repeatedly numfacts m-fact))
          trace (trace/get-trace (-> session
                                  (trace/with-tracing)
                                  (insert m-facts)
                                  (fire-rules)))]
      (is (= :add-facts (:type (first trace))))
      (is (= (count (:facts (first trace)))
             (* numfacts (dec (count (keys (m-fact)))))))
      (is (every? #(= Tuple (type %)) (:facts (first trace)))
        "Entity maps should have been inserted as Tuples"))))

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
    (is (= #{:all :one-to-one :unique-identity} (ancestors-fn :todo/title)))
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
        next-1 (->Tuple 1 :foo 43 3)
        fact-2 (->Tuple 1 :bar 42 2)
        next-2 (->Tuple 1 :bar 43 4)
        one-to-many (->Tuple 1 ::test/one-to-many 42 5)
        unique (->Tuple 1 ::test/unique-identity "my unique title" 6)
        unique-upsert (->Tuple 1 ::test/unique-identity "my new unique title" 7)
        unique-conflicting (->Tuple 2 ::test/unique-identity "my new unique title" 8)
        unique-value-conflicting (->Tuple 1 ::test/unique-value "foo" 9)
        unique-value-upsert (->Tuple 2 ::test/unique-value "bar" 10)
        unique-value (->Tuple 2 ::test/unique-value "foo" 11)]
;; attempting to fix the bug approach
;; when there is a one to one that is in the index (e a) remove itreplace it
    (testing "Initial state"
      (is (= {} (reset! state/fact-index {}))
          "Expected fact index to be {}")
      (is (fn? @state/ancestors-fn)
          "Expected ancestors-fn to be a fn"))

    (testing "Index one-to-one with empty state"
      (is (= {:insert fact-1} (update-index! fact-1)))
      (is (= @state/fact-index {:one-to-one {1 {:foo fact-1}}})))

    (testing "Index same e-a one-to-one should upsert"
      (is (= @state/fact-index {:one-to-one {1 {:foo fact-1}}}))
      (is (= {:insert next-1 :retract fact-1} (update-index! next-1)))
      (is (= @state/fact-index {:one-to-one {1 {:foo next-1}}})))

    (testing "Remove fact from index that does not exist"
      (is (= {:retract nil} (remove-from-fact-index! fact-1 (fact-index-path fact-1))))
      (is (= @state/fact-index {:one-to-one {1 {:foo next-1}}})))

    (testing "Same eid, different attribute should index the new fact"
      (is (= @state/fact-index {:one-to-one {1 {:foo next-1}}}))
      (is (= {:insert fact-2} (update-index! fact-2)))
      (is (= @state/fact-index {:one-to-one {1 {:foo next-1
                                                :bar fact-2}}})))

    ;; Check if this must be the case, seems like either 1. remove fn should just carry out instrs,
    ;; 2. we remove if e-a pair matches (not whole fact)
    (testing "Removing same eid, same attribute but different fact-id should not alter index and
              return false to indicate nothing was removed"
      (is (= @state/fact-index {:one-to-one {1 {:foo next-1
                                                :bar fact-2}}}))
      (is (= {:retract nil} (remove-from-fact-index! next-2 (fact-index-path next-2))))
      (is (= @state/fact-index {:one-to-one {1 {:foo next-1
                                                :bar fact-2}}})))

    (testing "One-to-many attrs do not affect fact index"
      (is (= @state/fact-index {:one-to-one {1 {:foo next-1
                                                :bar fact-2}}}))
      (is (= (update-index! one-to-many)
             {:insert one-to-many}))
      (is (= @state/fact-index {:one-to-one {1 {:foo next-1
                                                :bar fact-2}}})))

    (testing "Unique attrs should be indexed in own bucket and one-to-one"
      (is (= @state/fact-index
            {:one-to-one {1 {:foo next-1
                             :bar fact-2}}}))
      (is (= {:insert unique :retract nil} (update-index! unique)))
      (is (= @state/fact-index
            {:one-to-one {1 {:foo next-1
                             :bar fact-2
                             ::test/unique-identity unique}}
             :unique {(:a unique) {(:v unique) unique}}})))

    (testing "Unique attrs should be upserted if same eid and :unique/identity"
      (is (= @state/fact-index
            {:one-to-one {1 {:foo next-1
                             :bar fact-2
                             ::test/unique-identity unique}}
             :unique {::test/unique-identity {(:v unique) unique}}}))
      (is (= {:insert unique-upsert :retract unique} (update-index! unique-upsert)))
      (is (= @state/fact-index
            {:one-to-one {1 {:foo next-1
                             :bar fact-2
                             ::test/unique-identity unique-upsert}}
             :unique {::test/unique-identity {(:v unique-upsert) unique-upsert}}})))

    (testing "Unique attrs should generate error if diff eid and :unique/identity"
      (let [conflicting-insert-res (update-index! unique-conflicting)]
        (is (= @state/fact-index
              {:one-to-one {1 {:foo next-1
                               :bar fact-2
                               ::test/unique-identity unique-upsert}}
               :unique {::test/unique-identity {(:v unique-upsert) unique-upsert}}}))
        (is (= (mapv (juxt :a :v) (:insert conflicting-insert-res))
               [[::err/type :unique-conflict]
                [::err/existing-fact unique-upsert]
                [::err/failed-insert unique-conflicting]]))
        (is (= nil (:retract conflicting-insert-res)))
        (is (= @state/fact-index
              {:one-to-one {1 {:foo next-1
                               :bar fact-2
                               ::test/unique-identity unique-upsert}}
               :unique {(:a unique-upsert) {(:v unique-upsert) unique-upsert}}}))))

    (testing "Unique attrs should generate error if diff eid and :unique/value"
        (is (= {:insert unique-value :retract nil} (update-index! unique-value)))
        (is (= @state/fact-index
              {:one-to-one {1 {:foo next-1
                               :bar fact-2
                               ::test/unique-identity unique-upsert}
                            2 {::test/unique-value unique-value}}
               :unique {(:a unique-upsert) {(:v unique-upsert) unique-upsert}
                        (:a unique-value) {(:v unique-value) unique-value}}}))
        (let [conflicting-value-insert-res (update-index! unique-value-conflicting)]
          (is (= (mapv (juxt :a :v) (:insert conflicting-value-insert-res))
                [[::err/type :unique-conflict]
                 [::err/existing-fact unique-value]
                 [::err/failed-insert unique-value-conflicting]]))
          (is (= nil (:retract conflicting-value-insert-res)))
          (is (= @state/fact-index
                {:one-to-one {1 {:foo next-1
                                 :bar fact-2
                                 ::test/unique-identity unique-upsert}
                              2 {::test/unique-value unique-value}}
                 :unique {(:a unique-upsert) {(:v unique-upsert) unique-upsert}
                          (:a unique-value) {(:v unique-value) unique-value}}}))))

    (testing ":unique/value attrs should generate error if try upsert (same eid)"
      (is (= @state/fact-index
            {:one-to-one {1 {:foo next-1
                             :bar fact-2
                             ::test/unique-identity unique-upsert}
                          2 {::test/unique-value unique-value}}
             :unique {(:a unique-upsert) {(:v unique-upsert) unique-upsert}
                      (:a unique-value) {(:v unique-value) unique-value}}}))
      (let [unique-value-upsert-res (update-index! unique-value-upsert)]
        (is (= (mapv (juxt :a :v) (:insert unique-value-upsert-res))
              [[::err/type :unique-conflict]
               [::err/existing-fact unique-value]
               [::err/failed-insert unique-value-upsert]]))
        (is (= nil (:retract unique-value-upsert-res)))
        (is (= @state/fact-index
              {:one-to-one {1 {:foo next-1
                               :bar fact-2
                               ::test/unique-identity unique-upsert}
                            2 {::test/unique-value unique-value}}
               :unique {(:a unique-upsert) {(:v unique-upsert) unique-upsert}
                        (:a unique-value) {(:v unique-value) unique-value}}}))))

    (testing "Remove one-to-one fact from cardinality index when identical match"
      (let [_ (reset! state/fact-index {})
            _ (update-index! fact-1)]
        (is (= [true] (remove-fact-from-index! fact-1)))
        (is (= @state/fact-index {}))))

    (testing "Remove unique fact from cardinality, unique indices when identical match"
      (let [_ (reset! state/fact-index {})
            _ (update-index! unique)]
        (is (= [true true] (remove-fact-from-index! unique)))
        (is (= @state/fact-index {}))))))

(run-tests)
