(ns precept.macros-ns
  (:require [clara.rules.compiler :as com]))

(defmacro inner-macro [s]
  `['(my-ns/my-fn) :from ['~s :all]])

(defmacro outer-macro [y xs]
  (let [_ (println "CLJS NS outer macro" (com/cljs-ns))])
  `(into ['~y '~'<-] (eval '~xs)))

(defmacro macro-context [other-macros]
  (let [symbols (eval other-macros)
        _ (println "CLJS NS" (com/cljs-ns))
        _ (println "Expanded in macro context" symbols)]
    {:result `(list '~symbols '~'+ '~'further-rearrangement)}))



(macro-context
  (outer-macro ?sym-a (inner-macro ?sym-b)))

