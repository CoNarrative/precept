(ns precept.dsl
    (:require [clara.rules.compiler :as com]))

(defmacro entity
 [e]
 `['(clara.rules.accumulators/all) :from ['~e :all]])

(defmacro <-
 [fact-binding form]
 `(into ['~fact-binding '~'<-] ~form #_(eval '~form)))

(defmacro inner-macro [s]
  `['(my-ns/my-fn) :from ['~s :all]])

(defmacro outer-macro [y xs]
  (let [_ (println "CLJS NS outer macro" (com/cljs-ns))]
    `(into ['~y '~'<-] ~xs)))

(def special-forms #{'inner-macro 'outer-macro})

(defn add-ns-if-special-form [x]
  (let [special-form? (special-forms x)]
    (if (list? x)
      (map add-ns-if-special-form x)
      (if special-form?
        (symbol (str "precept.dsl/" (name x)))
        x))))

(defmacro macro-context [other-macros]
 (let [_ (println "other-macros" other-macros)
       namespaced-specials (map add-ns-if-special-form other-macros)
       symbols (eval namespaced-specials)
       _ (println "CLJS NS" (com/cljs-ns))
       _ (println "Expanded in macro context" symbols)]
      {:result `(list '~symbols '~'+ '~'further-rearrangement)}))



;(macro-context (outer-macro ?sym-a (inner-macro ?sym-b)))

