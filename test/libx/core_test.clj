(ns libx.core-test
    (:require [clojure.test :refer [run-tests]]
              [libx.lang-test]
              [libx.deflogical-test]
              [libx.macros-test]
              [libx.tuple-rule-test]
              [libx.util-test]))

(defn run []
  (for [ns
        ['libx.lang-test
         'libx.deflogical-test
         'libx.macros-test
         'libx.tuple-rule-test
         'libx.util-test]]
    (dosync (-> ns (in-ns) (run-tests)))))

(run)
