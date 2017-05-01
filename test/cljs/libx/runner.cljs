(ns libx.runner
   (:require [libx.subscribe-test]
             [libx.store-test]
             [doo.runner :refer-macros [doo-tests]]))

(doo-tests 'libx.subscribe-test
           'libx.store-test)

(defn ^:export main []
  (enable-console-print!)
  (println "Hello world!"))

(main)