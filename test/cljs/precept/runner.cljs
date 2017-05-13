(ns precept.runner
   (:require [precept.subscribe-test]
             [precept.store-test]
             [doo.runner :refer-macros [doo-tests]]))

(doo-tests 'precept.subscribe-test
           'precept.store-test)

(defn ^:export main []
  (enable-console-print!)
  (println "Hello world!"))

(main)