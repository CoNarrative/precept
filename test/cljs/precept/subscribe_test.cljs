(ns precept.subscribe-test
  (:require [cljs.test :refer [run-tests]]
            [precept.core :as core]
            [precept.tuplerules :refer [def-tuple-session]]
            [precept.state :as state])
  (:require-macros [cljs.test :refer [async use-fixtures testing is]]
                   [devcards.core :refer [deftest]]))
(enable-console-print!)
(def-tuple-session my-session)

(core/swap-session! my-session)
(swap! state/state update :subscriptions (fn [_] {}))

;(use-fixtures :once
;  {:before (fn [_]
;               (core/swap-session! my-session)
;               (swap! core/state update :subscriptions (fn [_] {}))
;  {:after (fn [_] nil)})

(defn clear-subs []
  (swap! state/state update :subscriptions (fn [_] {})))

(defn reset-store [] (reset! state/store))

(defn before-each []
  (clear-subs)
  (reset-store))

(def subdef [:foo])

(def the-update {:foo 1 :bar 2 :baz 3})

(defn do-update-for-sub [subdef]
  (swap! state/store update subdef (fn [_] the-update)))

(defn lens-for-sub [subdef] (get (:subscriptions @state/state) subdef))

(deftest subscribe-should-store-subscriptions-in-state-atom
  (async done
    (before-each)
    (is (= {} (:subscriptions @state/state)))
    (core/subscribe subdef)
    (is (contains? (:subscriptions @state/state) subdef))
    (done)))

(deftest subscriptions-should-update-on-store-change
  (async done
    (before-each)
    (core/subscribe subdef)
    (is (= nil @(lens-for-sub subdef)))
    (do-update-for-sub subdef)
    (is (= the-update @(lens-for-sub subdef)))
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
    (is (contains? (:subscriptions @state/state) subdef))
    (is (identical?
          (get-in @state/state [:subscriptions subdef])
          (core/subscribe subdef)))
    (done)))

(run-tests)