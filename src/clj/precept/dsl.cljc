(ns precept.dsl
  #?(:cljs (:require-macros [precept.dsl])))

#?(:clj
   (defmacro entity
     [e]
     `['(acc/all) :from ['~e :all]]))

#?(:clj
   (defmacro <-
     [fact-binding form]
     `(into ['~fact-binding '~'<-] (eval '~form))))

#?(:cljs
   (defn entity
     [eid]
     (precept.dsl/entity eid)))

#?(:cljs
   (defn <-
     [variable-binding special-form]
     (precept.dsl/<- variable-binding special-form)))

