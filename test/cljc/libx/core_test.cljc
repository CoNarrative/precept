(ns libx.core-test
  (:require [libx.core :as core]
            [libx.state :as state]
            [libx.util :as util]
            [libx.listeners :as l]
            [libx.tuplerules :refer [def-tuple-session]]
            [libx.schema :as schema]
            [libx.schema-fixture :refer [test-schema]]
            [clara.rules :as cr]
            [libx.test-helpers :as h]
    #?(:clj [clojure.test :refer [use-fixtures deftest is testing run-tests]]))
  (:import [libx.util Tuple]))


(defn reset-globals [f]
  (reset! state/store {})
  (reset! state/ancestors-fn nil)
  (reset! state/fact-index {})
  (f))

(use-fixtures :once reset-globals)

(defn nil-or-pred? [pred x]
  (or (nil? x) (pred x)))

(deftest view-model-test
  (let [n-facts 10
        eids [1 2]
        facts (h/mk-facts n-facts eids)
        session (h/create-test-session facts 'libx.core-test)
        ops (l/vec-ops session)
        added (:added ops)
        removed (:removed ops)]
    (is (= @state/store {}))
    (is (= Tuple (type (get-in @state/fact-index [:one-to-one 1 :test-attr/one-to-one]))))
    (is (= removed []))
    (is (= #{:test-attr/one-to-many :test-attr/one-to-one :test-attr/unique}
           (into #{} (map second added)))
        "Session change parser should have returned at least one of every test attribute")
    ;; Apply additions and removals!
    (do (core/apply-additions-to-view-model! added)
        (core/apply-removals-to-view-model! removed))
    (is (= (set (remove #(= % :test-attr/unique) (keys (get @state/store (first eids)))))
           #{:test-attr/one-to-many :test-attr/one-to-one})
        "Expected eid to have association with every supported except unique")
    (is (coll? (:test-attr/one-to-many (get @state/store (first eids))))
        "Expected one-to-many facts to be a collection")
    (is (= n-facts (count (:test-attr/one-to-many (get @state/store (first eids)))))
        "Expected one-to-many facts to equal number of facts given to each type at session
        creation")
    (is (string? (:test-attr/one-to-one (get @state/store (first eids))))
        "Expected a string value for one-to-one fact")
    (is (string? (or (:test-attr/unique (get @state/store (first eids)))
                     (:test-attr/unique (get @state/store (second eids)))))
        "Expected a string value for unique fact")
    (is (= (:v (h/max-fid-fact facts (first eids) :test-attr/one-to-one))
           (:test-attr/one-to-one (get @state/store (first eids)))))
    (is (= (:v (h/max-fid-fact facts (second eids) :test-attr/one-to-one))
           (:test-attr/one-to-one (get @state/store (second eids))))
        "Expected view-model to contain lastest value for :one-to-one fact")
    (is (nil-or-pred?
          #(= % (:v (h/max-fid-fact facts (first eids) :test-attr/unique)))
          (:test-attr/unique (get @state/store (first eids))))
      "Expected view-model to contain lastest value for :unique fact or no value for :unique fact")
    ; Remove everything that was added!
    (core/apply-removals-to-view-model! added)
    (is (= @state/store
           {1 {:test-attr/one-to-many '()}
            2 {:test-attr/one-to-many '()}}))))


(run-tests)
