;; TODO. Lens in CLJ
;(ns precept.subscribe-test
;  (:require ;[clojure.test :refer [deftest is testing run-tests]]
;            [cljs.test :refer-macros [async deftest is testing] :refer [run-tests]]
;            [precept.core :as core]
;            [precept.tuplerules :refer [def-tuple-session]]))
;
;(core/swap-session! @(def-tuple-session my-session))
;(swap! core/state update :subscriptions (fn [_] {}))
;(:subscriptions @core/state)
;
;(def subdef [:foo])
;
;(def sub-update {:foo 1 :bar 2 :baz 3})
;
;(defn do-update-for-sub [subdef]
;  (swap! core/store update subdef sub-update))
;
;(defn sub-value [subdef] (get (:subscriptions @core/state) subdef))
;
;(deftest subscribe-test
;  (testing "subscribe should store subscriptions in state"
;    (async done
;      (is (= {} (:subscriptions @core/state)))
;      (core/subscribe subdef)
;      (is (contains? (:subscriptions @core/state) subdef))
;      (done)))
;  (testing "subscriptions should update on state change"
;    (async done
;      (is (= nil (sub-value subdef)))
;      (do-update-for-sub subdef)
;      (is (= sub-update (sub-value subdef)))
;      (done)))
;  (testing "subscribe should return existing subscriptions"
;    (async done
;      (is (contains? (:subscriptions @core/state) subdef))
;      (is (= (:subscriptions @core/state) (core/subscribe subdef)))
;      (is (contains? (:subscriptions @core/state) subdef))
;      (done))))
;
;(run-tests)