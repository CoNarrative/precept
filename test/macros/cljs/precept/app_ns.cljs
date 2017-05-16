(ns precept.app-ns
   (:require-macros [precept.macros-ns :refer [outer-macro
                                               inner-macro
                                               macro-context]]))

(enable-console-print!)

(macro-context
  (outer-macro ?sym-a (inner-macro ?sym-b)))

