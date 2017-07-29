(ns precept.macros-test
    (:require [precept.macros :as macros]
              [clojure.test :refer [deftest run-tests testing is]]))

(def all-options (list 'precept.macros-test
                        :db-schema 'db-schema-ns
                        :client-schema 'client-schema-ns
                        :reload true))

(def no-options (list 'precept.macros-test))

(deftest options-map-test
  (is (= (macros/options-map no-options)
         {}))
  (is (= (macros/options-map all-options)
         {:db-schema 'db-schema-ns
          :client-schema 'client-schema-ns
          :reload true})))

(deftest sources-list-test
  (is (= (macros/sources-list all-options)
         (macros/sources-list no-options)
         (list 'precept.macros-test))))

(deftest precept->clara-options-test
  (let [cr-options '(:fact-type-fn :a
                     :ancestors-fn identity
                     :activation-group-fn
                       (precept.util/make-activation-group-fn :calc)
                     :activation-group-sort-fn
                       (precept.util/make-activation-group-sort-fn
                         [:action :calc :report :cleanup] :calc))]
    (is (= (macros/precept->clara-options
             (macros/merge-default-options
               (macros/options-map no-options)
               'identity)
             macros/precept-options-keys)
           cr-options))

    (is (= (macros/precept->clara-options
             (macros/merge-default-options
               (macros/options-map all-options)
               'identity)
             macros/precept-options-keys)
           cr-options))))

(run-tests)
