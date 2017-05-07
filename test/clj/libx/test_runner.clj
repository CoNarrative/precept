(ns libx.test-runner
    (:require [clojure.test :refer [run-tests]]
              [libx.lang-test]
              [libx.deflogical-test]
              [libx.macros-test]
              [libx.tuple-rule-test]
              [libx.query-test]
              [libx.util-test]
              [libx.listeners-test]))
              ;[libx.schema-test]))

(defn run []
  (for [ns ['libx.lang-test
            'libx.deflogical-test
            'libx.macros-test
            'libx.tuple-rule-test
            'libx.query-test
            'libx.util-test
            'libx.listeners-test]]
            ;'libx.schema-test]]
    (dosync (-> ns (in-ns) (run-tests)))))

(run)
