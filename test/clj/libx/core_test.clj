(ns libx.core-test
    (:require [clojure.test :refer [run-tests]]
              [libx.lang-test]
              [libx.deflogical-test]
              [libx.macros-test]
              [libx.tuple-rule-test]
              [libx.util-test]
              [libx.defaction-test]
              [libx.listeners-test]))

(defn run []
  (for [ns
        ['libx.lang-test
         'libx.deflogical-test
         'libx.macros-test
         'libx.tuple-rule-test
         'libx.util-test
         'libx.defaction-test
         'libx.listeners-test]]
    (dosync (-> ns (in-ns) (run-tests)))))

(run)
