;(ns precept.defaction-test
;  (:require [precept.macros :refer [defaction]]
;            [clojure.test :refer [use-fixtures deftest run-tests is testing]]
;            [clara.tools.tracing :as trace]
;            [precept.tuplerules :refer [def-tuple-session def-tuple-query]]))

;; TODO. Is failing because Tuple != vector
;; defaction in this form will likely not be part of api

;(def fact [1 :foo :tag])
;
;(defaction my-action fact)
;
;(def-tuple-session my-session)
;
;(defn find-insertions [trace]
;  (mapcat :facts (:add-facts (group-by :type trace))))
;
;(defn find-retractions [trace]
;  (mapcat :facts (:retract-facts (group-by :type trace))))
;
;(deftest defaction-test
;  (let [test-session (my-action (trace/with-tracing my-session))]
;    (testing "Inserted the fact?"
;      (is (= (find-insertions (trace/get-trace test-session))
;             (list fact))))
;    (testing "Retracted the fact?"
;      (is (= (find-retractions (trace/get-trace test-session))
;             (list fact))))))
;
;
;(run-tests)
