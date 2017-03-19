(ns todomvc.macros
    #?(:cljs (:require-macros [clara.rules :refer [defsession]]))
    #?(:clj
       (:require [clara.rules :refer [defsession fire-rules insert query defquery]])))

(defmacro def-tuple-session
  "Wrapper around Clara's `defsession` macro.
  Preloads query helpers."
  [name & sources-and-options]
  `(defsession
     ~name
     'todomvc.util
     ~@sources-and-options
     :fact-type-fn ~'(fn [[e a v]] a)
     :ancestors-fn ~'(fn [type] [:all])))

