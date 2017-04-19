;(ns libx.schema-test
;  (:require [clojure.test :refer [deftest testing is run-tests]]
;            [libx.core :refer [state schema-insert] :as core]
;            [libx.tuplerules :refer [def-tuple-session]]
;            [libx.schema-fixture :refer [test-schema]]))
;
;(def-tuple-session test-session 'libx.schema-test)
;
;(deftest schema-test
;  (testing "Writing schema to state atom"
;   (is (= (:schema @state) (:schema (core/init-schema test-schema))))))
;  ;(testing "Insert using schema in state atom"
;  ;  (is (= (core/schema-insert test-session unique-identity-fact))))))
;
;
;(run-tests)
