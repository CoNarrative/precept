(ns precept.macros-cljc-ns
  #?(:cljs (:require-macros [precept.macros])))
;
#?(:cljs
    (defmacro macro-context [x]
      `(precept.macros-ns/macro-context ~x)))


