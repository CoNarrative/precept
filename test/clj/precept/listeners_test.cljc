(ns precept.listeners-test
  (:require [clojure.test :refer [use-fixtures is deftest testing run-tests]]
            [clara.rules.accumulators :as acc]
            [clara.rules :refer [query fire-rules] :as cr]
            [precept.listeners :as l]
            [precept.state :as state]
            [precept.query :as q]
            [precept.util :refer [guid ->Tuple] :as util]
            [precept.rules :refer [rule session]])
  (:import [precept.util Tuple]))

(defn reset-globals [f]
  (reset! state/fact-index {})
  (util/make-ancestors-fn)
  (f)
  (reset! state/fact-index {})
  (util/make-ancestors-fn))

(use-fixtures :once reset-globals)
(use-fixtures :each reset-globals)

(defn trace [& args]
  (comment (apply prn args)))

(rule insert-logical-for-every-a
  [?f <- [?e :attr/a]]
  =>
  (trace "Found " ?f "Inserting logical fact" [?e :attr/logical-insert ?f])
  (util/insert! [?e :attr/logical-insert ?f]))

(rule retract-a-fact-that-caused-a-logical-insertion
  [[_ :attr/logical-insert ?f]]
  =>
  (trace "Found " ?f " Retracting its condition for existing")
  (cr/retract! ?f))

(rule accumulate-count
  [?facts <- (acc/all) :from [:all]]
  =>
  (trace "All facts" ?facts)
  (do nil))

(def background-facts (repeatedly 5 #(vector (guid) :junk 42)))

(deftest trace-parsing-fns
  (let [fact-t (inc @state/fact-id)
        traced-session (-> @(session trace-parsing-session)
                          (l/replace-listener)
                          (util/insert
                            (into
                              [[1 :attr/a "state-0"]
                               [1 :attr/b "state-0"]]
                              background-facts))
                          (util/retract
                            (->Tuple 1 :attr/a "state-0" fact-t)
                            #_[1 :attr/a "state-0"])
                          (fire-rules))
        traces (l/fact-traces traced-session)
        keyed-by-type (l/trace-by-type (first traces))
        additions (l/insertions keyed-by-type)
        removals (l/retractions keyed-by-type)
        split-ops (l/split-ops (first traces))
        vectorized-trace (l/vectorize-trace (first traces))
        vec-ops (l/vec-ops traced-session)]

    (testing "fact-traces should return seq of trace vectors"
      (is (and vector? (not (> 1 (count traces))))
          "More than one item in trace vector. May have more than one tracer on session?")
      (is (every? vector? traces))
      (is (every? (comp map? first) traces))
      (is (every? (comp #(contains? % :facts) first) traces))
      (is (every? (comp #(contains? % :type) first) traces)))

    (testing ":facts in fact-traces should be Tuples"
      (is (every? (comp #(every? (partial instance? Tuple) %) :facts first) traces)
          "Expected every item in :facts to be a Tuple"))

    (testing "trace-by-type should return trace keyed by clara op"
      (let [clara-fact-ops #{:add-facts :add-facts-logical :retract-facts :retract-facts-logical}]
        (is (every? clara-fact-ops (keys keyed-by-type)))))

    (testing "insertions should return add operations only"
      (is (every? #{:add-facts :add-facts-logical} (keys additions))))

    (testing "removals should return retract operations only"
      (is (every? #{:retract-facts :retract-facts-logical} (keys removals))))

    (testing "vectorize-trace should return trace with each Tuple in :facts as vector"
      (is (every? map? vectorized-trace))
      (is (every? #(contains? % :facts) vectorized-trace))
      (is (every? #(contains? % :type) vectorized-trace))
      (is (every? (comp #(every? vector? %) :facts) vectorized-trace)))

    (testing "vec-ops should return m of :added, :removed as vec of vecs"
      (is (= '(:added :removed) (keys vec-ops)))
      (is (= (:removed vec-ops) [])
          "Fact was added then removed. Net removals should be []"))

    (testing "ops-0 :added"
      (is (= (set (:added vec-ops))
             (set (conj background-facts [1 :attr/b "state-0"])))))))


(deftest listeners-state-transitions
  (let [test-session @(session the-session
                        'precept.listeners-test
                        'precept.query)
        state-0-inserts (into
                         [(->Tuple 123 :attr/a "state-0" 0)
                          (->Tuple 123 :attr/b "state-0" 1)]
                         (mapv util/vec->record background-facts))
        state-0-retracts []
        state-0 (-> test-session
                  (l/replace-listener)
                  (util/insert state-0-inserts)
                  (fire-rules))
        ops-0 (l/vec-ops state-0)
        ent-0 (q/entityv state-0 123)

        state-1-inserts [123 :attr/b "state-1"]
        state-1-retracts (->Tuple 123 :attr/b "state-0" 1)
        state-1 (-> state-0
                  (l/replace-listener)
                  (util/retract state-1-retracts)
                  (util/insert state-1-inserts)
                  (fire-rules))
        ops-1 (l/vec-ops state-1)
        ent-1 (q/entityv state-1 123)

        state-2-inserts [123 :attr/b "state-2"]
        state-2-retracts []
        state-2 (-> state-1
                  (l/replace-listener)
                  (util/insert state-2-inserts)
                  (fire-rules))
        ops-2 (l/vec-ops state-2)
        ent-2 (q/entityv state-2 123)]

    (testing "session-0: net additions"
      (is (= (set (conj background-facts
                   [123 :attr/b "state-0"]))
             (set (:added ops-0)))))

    (testing "session-0: net removals"
      (is (= (:removed (l/vec-ops state-0)) [])))

    (testing "session-1: net additions"
      (is (= (:added (l/vec-ops state-1)) (vector state-1-inserts))))

    (testing "session-1: net removals"
      (is (= (:removed (l/vec-ops state-1)) (vector (util/record->vec state-1-retracts)))))

    (testing "session-2: net additions"
      (is (= (:added (l/vec-ops state-2)) [[123 :attr/b "state-2"]])
          (into #{} (:added ops-2))))

    (testing "session-2 net removals"
      (is (= (:removed (l/vec-ops state-2)) [[123 :attr/b "state-1"]]))))) ;; affected by
      ;; one-to-one enforcement

(run-tests)
