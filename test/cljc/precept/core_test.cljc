(ns precept.core-test
  (:require [precept.core :as core]
            [precept.state :as state]
            [precept.util :refer [->Tuple] :as util]
            [precept.listeners :as l]
            [precept.spec.sub :as sub]
            [precept.spec.error :as err]
            [precept.tuplerules :refer [def-tuple-session]]
            [precept.schema :as schema]
            [precept.schema-fixture :refer [test-schema]]
            [clara.rules :as cr]
            [precept.test-helpers :as h]
    #?(:clj [clojure.test :refer [use-fixtures deftest is testing run-tests]]))
  (:import [precept.util Tuple]))


(defn reset-globals [f]
  (reset! state/store {})
  (reset! state/ancestors-fn nil)
  (reset! state/fact-index {})
  (f))

(use-fixtures :once reset-globals)

(defn nil-or-pred? [pred x]
  (or (nil? x) (pred x)))

(deftest view-model-test
  (let [n-facts 5
        eids [1 2]
        facts (h/mk-facts n-facts eids)
        subs [(->Tuple 3 ::sub/request :test-attr/my-sub 10)
              (->Tuple 3 ::sub/response {:my-kw facts} 11)]
        session (h/create-test-session (concat facts subs) 'precept.core-test)
        ops (l/vec-ops session)
        added (:added ops)
        removed (:removed ops)]
    (is (= @state/store {}))
    (is (= Tuple (type (get-in @state/fact-index [:one-to-one 1 :test-attr/one-to-one]))))
    (is (= removed []))
    (is (every? #{:test-attr/one-to-many :test-attr/one-to-one :test-attr/unique
                  ::sub/request ::sub/response
                  ::err/type ::err/existing-fact ::err/failed-insert}
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
    (is (= 42 (:test-attr/one-to-one (get @state/store (first eids))))
        "Expected a string value for one-to-one fact")
    (is (= 42 (or (:test-attr/unique (get @state/store (first eids)))
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
    (is (= (frequencies (list ::sub/request ::sub/response))
           (frequencies (keys (get @state/store 3)))))
    (is (= (get-in @state/store [3 ::sub/response :my-kw])
           [{:db/id 1
             :test-attr/one-to-many '(42 42 42 42 42)
             :test-attr/one-to-one 42
             :test-attr/unique 42}
            {:db/id 2
             :test-attr/one-to-many '(42 42 42 42 42)
             :test-attr/one-to-one 42
             :test-attr/unique 42}]))))
    ; Remove everything that was added!
    ;(core/apply-removals-to-view-model! added)
    ;(is (= {} @state/store))))

(run-tests)
