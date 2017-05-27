(ns precept.macros-test
    (:require [precept.tuplerules :refer [def-tuple-session]]
              [precept.util :as util]
              [precept.core :as core]
              [clara.rules :refer [defsession defrule]]
              [clara.rules.accumulators :as acc]
              [clojure.test :refer [deftest run-tests testing is]]
              [precept.schema :as schema]
              [precept.schema-fixture :refer [test-schema]]
              [precept.dsl :refer [<- entity]])
    (:import [precept.util Tuple]))

(deftest def-tuple-session-test
  (testing "Macroexpansion should be the same as equivalent
            arguments to defsession"
      (is (= (macroexpand `(defsession ~'foo
                            'precept.impl.rules
                            'precept.macros-test
                            :fact-type-fn :a
                            :ancestors-fn (util/make-ancestors-fn
                                            (schema/init! (select-keys {}
                                                            [:db-schema :client-schema])))
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

  (testing "Expand :db-schema opt to ancestors fn"
      (is (= (macroexpand `(defsession ~'foo
                             'precept.impl.rules
                             'precept.macros-test
                             :fact-type-fn :a
                             :ancestors-fn (util/make-ancestors-fn
                                             (schema/init!
                                               (select-keys
                                                 {:db-schema ~precept.schema-fixture/test-schema}
                                                 [:db-schema :client-schema])))
                             :activation-group-fn (util/make-activation-group-fn ~core/default-group)
                             :activation-group-sort-fn (util/make-activation-group-sort-fn
                                                         ~core/groups
                                                         ~core/default-group)))
            (macroexpand `(def-tuple-session ~'foo
                            'precept.macros-test
                            :db-schema ~precept.schema-fixture/test-schema)))))

;; TODO. Probably better off testing functionality over expansion at this point
  (testing "Expand :client-schema and :db-schema opts to ancestors fn"
    (is (= (macroexpand `(defsession ~'foo
                           'precept.impl.rules
                           'precept.macros-test
                           :fact-type-fn :a
                           :ancestors-fn (util/make-ancestors-fn
                                           (schema/init!
                                             (select-keys
                                               {:db-schema ~precept.schema-fixture/test-schema
                                                :client-schema ~precept.schema-fixture/test-schema}
                                               [:db-schema :client-schema])))
                           :activation-group-fn (util/make-activation-group-fn ~core/default-group)
                           :activation-group-sort-fn (util/make-activation-group-sort-fn
                                                       ~core/groups
                                                       ~core/default-group)))
          (macroexpand `(def-tuple-session ~'foo
                          'precept.macros-test
                          :db-schema ~precept.schema-fixture/test-schema
                          :client-schema ~precept.schema-fixture/test-schema))))))

(run-tests)
