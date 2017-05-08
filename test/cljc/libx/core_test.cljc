(ns libx.core-test
  (:require [libx.core :as core]
            [libx.state :as state]
            [libx.util :as util]
    #?(:clj [clojure.test :refer [use-fixtures deftest is testing run-tests]])))


(defn reset-globals [_]
  (reset! state/store {})
  (util/make-ancestors-fn))

(use-fixtures :once reset-globals)

(deftest store-test
  (is (= @state/store false)))


(run-tests)