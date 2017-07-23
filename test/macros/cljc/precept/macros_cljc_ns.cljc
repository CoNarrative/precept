(ns precept.macros-cljc-ns
    #?(:clj
       (:require [precept.rules :as rules]
                 [precept.macros-ns]
                 [cljs.env :as env]
                 [clara.rules.compiler :as com]))
    #?(:cljs (:require-macros [precept.macros-ns])))

#?(:clj
    (defmacro macro-context [x]
      `(precept.macros-ns/macro-context ~x)))

#?(:clj
   (defmacro inner-macro [x]
     `(precept.macros-ns/inner-macro ~x)))

#?(:clj
   (defmacro some-macro [namespace]
     (let [v (get-in @env/*compiler* [:clara.macros/productions])
               ;[:clara.macros/productions (com/cljs-ns)])
           ;_ (swap! env/*compiler* dissoc :clara.macros/productions)]
           _ (swap! env/*compiler* dissoc :clara.macros/productions)
           v2 (get-in @env/*compiler*
                [:clara.macros/productions])]
                 ;[:clara.macros/productions (com/cljs-ns)])]
       `(vector
          "PRODUCTIONS BEFORE:" ~v
          "PRODUCTIONS AFTER:" ~v2
          "NAMESPACE: " ~namespace))))
     ;`(precept.macros-ns/some-macro ~namespace)))


;; productions -> ns appears keyed by "name" where
;; "name" is the map/def itself, val is the same

;#(:clj
;   (defmacro get-productions [namespace]
;    `(precept.macros-ns/get-productions ~namespace)))
