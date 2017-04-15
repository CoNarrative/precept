(ns libx.subscribe-test
  (:require [cljs.test :refer [run-tests]]
            [libx.core :as core]
            [libx.tuplerules :refer [def-tuple-session]])
  (:require-macros [cljs.test :refer [async use-fixtures testing is]]
                   [devcards.core :refer [deftest]]))
(def-tuple-session my-session)

(core/swap-session! my-session)
(swap! core/state update :subscriptions (fn [_] {}))

(use-fixtures :once
  {:before (fn [_]
               (core/swap-session! my-session)
               (swap! core/state update :subscriptions (fn [_] {})))}
  {:after (fn [_] nil)})
(:subscriptions @core/state)

(defn clear-subs []
  (swap! core/state update :subscriptions (fn [_] {})))

(defn reset-store [] (reset! core/store))

(defn before-each []
  (clear-subs)
  (reset-store))

(def subdef [:foo])

(def sub-update (fn [_] {:foo 1 :bar 2 :baz 3}))

(defn do-update-for-sub [subdef]
  (swap! core/store update subdef sub-update))

(defn lens-for-sub [subdef] (get (:subscriptions @core/state) subdef))

(deftest subscribe-should-store-subscriptions-in-state
  (async done
    (before-each)
    (is (= {} (:subscriptions @core/state)))
    (core/subscribe subdef)
    (is (contains? (:subscriptions @core/state) subdef))
    (done)))

(deftest subscriptions-should-update-on-state-change
  (async done
    (before-each)
    (core/subscribe subdef)
    (is (= nil @(lens-for-sub subdef)))
    (do-update-for-sub subdef)
    (is (= (sub-update nil) @(lens-for-sub subdef)))
    (done)))

(deftest dereffed-subscribe-returns-map-when-sub-has-data
  (async done
    (before-each)
    (do-update-for-sub subdef)
    (is (map? @(core/subscribe subdef)))
    (done)))

(deftest subscribe-should-return-existing-subscriptions
  (async done
    (before-each)
    (core/subscribe subdef)
    (is (contains? (:subscriptions @core/state) subdef))
    (is (identical?
          (get-in @core/state [:subscriptions subdef])
          (core/subscribe subdef)))
    (done)))

(run-tests)