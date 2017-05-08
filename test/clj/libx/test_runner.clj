(ns libx.test-runner
    (:require [clojure.test :refer [run-tests]]
              [libx.core-test]
              [libx.lang-test]
              [libx.deflogical-test]
              [libx.macros-test]
              [libx.tuple-rule-test]
              [libx.query-test]
              [libx.util-test]
              [libx.listeners-test]))

(defn run []
  (for [ns ['libx.core-test
            'libx.lang-test
            'libx.deflogical-test
            'libx.macros-test
            'libx.tuple-rule-test
            'libx.query-test
            'libx.util-test
            'libx.listeners-test]]
    (dosync (-> ns (in-ns) (run-tests)))))

(run)
