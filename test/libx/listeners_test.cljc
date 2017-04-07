(ns libx.listeners-test
  (:require [clojure.test :refer [is deftest testing run-tests]]
            [clara.rules.accumulators :as acc]
            [clara.rules :refer [insert-all insert! retract! query fire-rules]]
            [libx.listeners :as l]
            [libx.util :refer [guid clara-tups->tups insert retract qa- entityv]]
            [libx.tuplerules :refer [def-tuple-rule def-tuple-session]]))

(def-tuple-rule insert-logical-for-every-a
  [?f <- [?e :attr/a]]
  =>
  (insert! [?e :attr/logical-insert ?f]))

(def-tuple-rule retract-a-fact-that-caused-a-logical-insertion
  [[_ :attr/logical-insert ?f]]
  =>
  ;(println "Found " ?f " Retracting its condition for existing")
  (retract! ?f))

(def-tuple-rule accumulate-count
  [?facts <- (acc/count) :from [:all]]
  =>
  (do nil))

(def background-facts (repeatedly 5 #(vector (guid) :junk 42)))

(deftest listeners-state-transitions
  (let [test-session @(def-tuple-session the-session 'libx.listeners-test)
        state-0 (-> test-session
                  (l/add-listener)
                  (insert-all
                    (into
                      [[123 :attr/a "state-0"]
                       [123 :attr/b "state-0"]]
                      background-facts))
                  (fire-rules))
        ops-0 (l/split-ops (first (l/fact-events state-0)))
        ent-0 (entityv state-0 123)
        state-1 (-> state-0
                  (l/remove-fact-listeners)
                  (l/add-listener)
                  (retract [123 :attr/b "state-0"])
                  (insert [123 :attr/b "state-1"])
                  (fire-rules))
        ops-1 (l/split-ops (first (l/fact-events state-1)))
        ent-1 (entityv state-1 123)
        state-2 (-> state-1
                  (l/remove-fact-listeners)
                  (l/add-listener)
                  (insert [123 :attr/b "state-2"])
                  (fire-rules))
        ops-2 (l/split-ops (first (l/fact-events state-2)))
        ent-2 (entityv state-2 123)]
    (testing "state-0"
      (is (= (into (set background-facts) ent-0)
             (set (:added ops-0))))
      (is (every? #(#{:attr/a :attr/logical-insert} (second %))
            (:removed ops-0))))
    (testing "state-1"
      (is (= (into #{} ent-1)
             (into #{} (:added ops-1))))
      (is (every? #(= (last %) "state-0")
            (:removed ops-1))))
    (testing "state-2"
      (is (every? (into #{} ent-2)
                  (into #{} (:added ops-2))))
      (is (every? nil? (:removed ops-2))))))

(run-tests)
