(ns libx.macros-test
    (:require [libx.tuplerules :refer [def-tuple-session]]
              [clara.rules :refer [defsession]]
              [clojure.test :refer [deftest run-tests testing is]])
    (:import [libx.util Tuple]))

(deftest defn-tuple-session-test
  (testing "macroexpansion should be the same as equivalent arguments to defsession"
    (let [clara-session '(defsession foo 'libx.util 'libx.macros-test
                            :fact-type-fn :a
                            :ancestors-fn (fn [type] [:all]))
          wrapper       '(def-tuple-session foo 'libx.macros-test)]
      (is (= (macroexpand clara-session) (macroexpand wrapper))))))

(run-tests)

(libx.tuplerules/def-tuple-rule libx-print-facts
  [?fact <- [_ :attr]]
  ;[?fact <- []]
  => (println "Libx printing" ?fact))

(clara.rules/defrule clara-print-facts
  ;[?fact <- :attr]
  [?fact <- :all (= (:e this) 124)] ;(= ?a :attr) (= ?v "clara")]
  => (println "Clara printing----------------" ?fact))

(defsession test-cr-session
  'libx.macros-test/clara-print-facts
  ;'libx.util
  :fact-type-fn :a
  :ancestors-fn (fn [type] [:all]))

(def cr-w-fact (-> test-cr-session
                 (clara.rules/insert (Tuple. 123 :attr "clara"))
                 (clara.rules/fire-rules)))

;(def-tuple-session test-libx-session 'libx.macros-test/libx-print-facts)

;(def libx-w-fact (-> test-libx-session
;                   (libx.util/insert [123 :attr "libx"])
;                   (clara.rules/fire-rules)))

