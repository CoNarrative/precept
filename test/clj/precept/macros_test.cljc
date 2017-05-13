(ns precept.macros-test
    (:require [precept.tuplerules :refer [def-tuple-session]]
              [precept.util :as util]
              [precept.core :as core]
              [clara.rules :refer [defsession]]
              [clojure.test :refer [deftest run-tests testing is]]
              [precept.schema :as schema])
    (:import [precept.util Tuple]))

(deftest def-tuple-session-test
  (testing "Macroexpansion should be the same as equivalent
            arguments to defsession"
      (is (= (macroexpand `(defsession ~'foo
                            'precept.impl.rules
                            'precept.macros-test
                            :fact-type-fn :a
                            :ancestors-fn (util/make-ancestors-fn)
                            :activation-group-fn (util/make-activation-group-fn ~core/default-group)
                            :activation-group-sort-fn (util/make-activation-group-sort-fn
                                                        ~core/groups
                                                        ~core/default-group)))
             (macroexpand '(def-tuple-session foo 'precept.macros-test)))))

  (testing "Allow overwrite defaults"
    (let [clara-session `(defsession ~'foo
                           'precept.impl.rules
                           'precept.macros-test
                           :fact-type-fn ~'(fn [x] (or (:a x)
                                                     (:b x)))
                           :ancestors-fn ~'(fn [x] [:all :foo])
                           :activation-group-fn (util/make-activation-group-fn ~core/default-group)
                           :activation-group-sort-fn (util/make-activation-group-sort-fn
                                                        ~core/groups
                                                        ~core/default-group))
          wrapper       '(def-tuple-session foo
                           'precept.macros-test
                           :fact-type-fn (fn [x] (or (:a x)
                                                     (:b x)))
                           :ancestors-fn (fn [x] [:all :foo]))]
      (is (= (macroexpand clara-session) (macroexpand wrapper)))))

  (testing "Expand schema opt to ancestors fn"
    (is (= (macroexpand `(defsession ~'foo
                           'precept.impl.rules
                           'precept.macros-test
                           :fact-type-fn :a
                           :ancestors-fn (util/make-ancestors-fn (schema/schema->hierarchy
                                                                    ~precept.schema/precept-schema))
                           :activation-group-fn (util/make-activation-group-fn ~core/default-group)
                           :activation-group-sort-fn (util/make-activation-group-sort-fn
                                                       ~core/groups
                                                       ~core/default-group)))
          (macroexpand `(def-tuple-session ~'foo
                          'precept.macros-test
                          :schema ~precept.schema/precept-schema))))))

(run-tests)
