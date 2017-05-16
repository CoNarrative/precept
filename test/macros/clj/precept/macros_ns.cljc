(ns precept.macros-ns)

(defmacro inner-macro [s]
  `['(my-ns/my-fn) :from ['~s :all]])

(defmacro outer-macro [y xs]
  `(into ['~y '~'<-] (eval '~xs)))

(defmacro macro-context [other-macros]
  (let [symbols (eval other-macros)
        _ (println "Expanded in macro context" symbols)]
    {:result `(list '~symbols '~'+ '~'further-rearrangement)}))



(macro-context
  (outer-macro ?sym-a (inner-macro ?sym-b)))

