(ns precept.test-runner
    (:require [clojure.test :refer [run-tests]]
              [precept.core-test]
              [precept.lang-test]
              [precept.define-test]
              [precept.macros-test]
              [precept.rule-test]
              [precept.query-test]
              [precept.util-test]
              [precept.listeners-test]))

(defn run []
  (for [ns ['precept.core-test
            'precept.lang-test
            'precept.define-test
            'precept.macros-test
            'precept.rule-test
            'precept.query-test
            'precept.util-test
            'precept.listeners-test]]
    (dosync (-> ns (in-ns) (run-tests)))))

(run)
