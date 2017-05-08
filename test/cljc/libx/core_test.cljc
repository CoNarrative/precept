(ns libx.core-test
  (:require [libx.core :as core]
            [libx.state :as state]
            [libx.util :as util]
            [libx.listeners :as l]
            [libx.tuplerules :refer [def-tuple-session]]
            [libx.schema :as schema]
            [libx.schema-fixture :refer [test-schema]]
            [clara.rules :as cr]
    #?(:clj [clojure.test :refer [use-fixtures deftest is testing run-tests]]))
  (:import [libx.util Tuple]))


(defn reset-globals [f]
  (reset! state/store {})
  (reset! state/ancestors-fn nil)
  (reset! state/fact-index {})
  (f))

(use-fixtures :once reset-globals)

(defn rand-str [] (rand-nth ["foo" "bar" "baz" "quux"]))

(defn mk-facts [n-facts eid]
  (let [one-to-one-facts (repeatedly n-facts
                           #(util/vec->record [eid :test-attr/one-to-one (rand-str)]))
          unique-facts (repeatedly n-facts
                         #(util/vec->record [eid :test-attr/unique (rand-str)]))
          one-to-many-facts (repeatedly n-facts
                              #(util/vec->record [eid :test-attr/one-to-many (rand-str)]))]
     (into [] (concat one-to-one-facts unique-facts one-to-many-facts))))

(defn max-fid-fact [facts a]
  (apply max-key :t (filter #(= (:a %) a) facts)))

(defn create-test-session
  "Creates a session with `n-facts` of each type of fact supported by schema.
  Derives ancestry from test-schema. Adds fact listener to session. Returns session."
  [facts]
  (let [hierarchy (schema/schema->hierarchy test-schema)
        ancestors-fn (util/make-ancestors-fn hierarchy)
        session @(def-tuple-session core-test-session
                   'libx.core-test
                   :ancestors-fn ancestors-fn
                   :activation-group-fn (util/make-activation-group-fn core/default-group)
                   :activation-group-sort-fn (util/make-activation-group-sort-fn
                                               core/groups core/default-group))]
    (-> session
      (l/replace-listener)
      (util/insert facts)
      (cr/fire-rules))))

(defn apply-additions-to-view-model! [tuples]
  (doseq [[e a v] tuples]
    (let [ancestry (@state/ancestors-fn a)]
      (cond
        (ancestry :one-to-one) (swap! state/store assoc-in [e a] v)
        (ancestry :one-to-many) (swap! state/store update-in [e a] conj v)))))

;; TODO.
(defn apply-removals-to-view-model! [tuples]
  (doseq [[e a v] tuples]
    (let [ancestry (@state/ancestors-fn a)]
      (cond
        (ancestry :one-to-one) (swap! state/store util/dissoc-in [e a])
        (ancestry :one-to-many) (swap! state/store update-in [e a] (fn [xs] (remove #(= v %) xs)))))))

(deftest view-model-test
  (let [n-facts 10
        eid 1
        facts (mk-facts n-facts eid)
        session (create-test-session facts)
        ops (l/vec-ops session)
        added (:added ops)
        removed (:removed ops)]
    (is (= @state/store {}))
    (is (= Tuple (type (get-in @state/fact-index [:one-to-one 1 :test-attr/one-to-one]))))
    (is (= removed []))
    (is (= #{:test-attr/one-to-many :test-attr/one-to-one :test-attr/unique}
           (into #{} (map second added)))
        "Session change parser should have returned at least one of every test attribute")
    (do (apply-additions-to-view-model! added)
        (apply-removals-to-view-model! removed))
    (is (= #{:test-attr/unique :test-attr/one-to-many :test-attr/one-to-one}
           (set (keys (get @state/store eid))))
        "Expected eid to have association with every supported fact type")
    (is (coll? (:test-attr/one-to-many (get @state/store eid)))
        "Expected one-to-many facts to be a collection")
    (is (= n-facts (count (:test-attr/one-to-many (get @state/store eid))))
        "Expected one-to-many facts to equal number of facts given to each type at session
        creation")
    (is (string? (:test-attr/one-to-one (get @state/store eid)))
        "Expected a string value for one-to-one fact")
    (is (string? (:test-attr/unique (get @state/store eid)))
        "Expected a string value for unique fact")
    (is (= (:v (max-fid-fact facts :test-attr/one-to-one))
           (:test-attr/one-to-one (get @state/store eid)))
        "Expected view-model to contain lastest value for :one-to-one fact")))
    ;; TODO. Fails because unique is not enforced as also a one-to-one in fact-index
    ;(is (= (:v (max-fid-fact facts :test-attr/unique))
    ;      (:test-attr/unique (get @state/store eid)))
    ;  "Expected view-model to contain lastest value for :unique fact")))


(run-tests)
