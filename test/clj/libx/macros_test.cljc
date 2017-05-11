(ns libx.macros-test
    (:require [libx.tuplerules :refer [def-tuple-session]]
              [libx.util :as util]
              [libx.core :as core]
              [clara.rules :refer [defsession]]
              [clojure.test :refer [deftest run-tests testing is]]
              [libx.schema :as schema])
    (:import [libx.util Tuple]))

(deftest def-tuple-session-test
  (testing "Macroexpansion should be the same as equivalent
            arguments to defsession"
      (is (= (macroexpand `(defsession ~'foo
                            'libx.macros-test
                            :fact-type-fn :a
                            :ancestors-fn (util/make-ancestors-fn)
                            :activation-group-fn (util/make-activation-group-fn ~core/default-group)
                            :activation-group-sort-fn (util/make-activation-group-sort-fn
                                                        ~core/groups
                                                        ~core/default-group)))
             (macroexpand '(def-tuple-session foo 'libx.macros-test)))))

  (testing "Allow overwrite defaults"
    (let [clara-session `(defsession ~'foo
                           'libx.macros-test
                           :fact-type-fn ~'(fn [x] (or (:a x)
                                                     (:b x)))
                           :ancestors-fn ~'(fn [x] [:all :foo])
                           :activation-group-fn (util/make-activation-group-fn ~core/default-group)
                           :activation-group-sort-fn (util/make-activation-group-sort-fn
                                                        ~core/groups
                                                        ~core/default-group))
          wrapper       '(def-tuple-session foo
                           'libx.macros-test
                           :fact-type-fn (fn [x] (or (:a x)
                                                     (:b x)))
                           :ancestors-fn (fn [x] [:all :foo]))]
      (is (= (macroexpand clara-session) (macroexpand wrapper)))))

  (testing "Expand schema opt to ancestors fn"
    (is (= (macroexpand `(defsession ~'foo
                           'libx.macros-test
                           :fact-type-fn :a
                           :ancestors-fn (util/make-ancestors-fn ~(schema/schema->hierarchy
                                                                    libx.schema/libx-schema))
                           :activation-group-fn (util/make-activation-group-fn ~core/default-group)
                           :activation-group-sort-fn (util/make-activation-group-sort-fn
                                                       ~core/groups
                                                       ~core/default-group)))
          (macroexpand `(def-tuple-session ~'foo
                          'libx.macros-test
                          :schema ~libx.schema/libx-schema))))))

(run-tests)
