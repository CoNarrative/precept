;(ns precept.dsl-ns
;    (:require [cljs.js :as cljsjs])
;    #?(:cljs (:require-macros [precept.dsl-ns])))
;
;#?(:clj
;   (defmacro inner-macro [s]
;     `['(my-ns/my-fn) :from ['~s :all]]))
;
;#?(:clj
;   (defmacro outer-macro [y xs]
;     `(into ['~y '~'<-] (eval '~xs))))
;
;(defn eval-in-context [forms context]
;  (if (nil? context)
;      (eval forms)
;      (cljsjs/eval
;        (cljsjs/empty-state)
;        (require 'precept.dsl-ns)
;        forms
;        (fn [res] res))))
;
;
;#?(:clj
;    (defmacro macro-context [other-macros]
;         (let [cljsns (clara.rules.compiler/cljs-ns)
;               _ (println "CLJS NS" cljsns)
;               symbols (eval-in-context other-macros cljsns)
;               ;symbols (eval other-macros)
;               _ (println "Expanded in macro context" symbols)]
;            {:result `'~symbols})))
;
;#?(:cljs
;   (defn inner-macro
;     [inner-arg]
;     (precept.dsl-ns/inner-macro inner-arg)))
;
;#?(:cljs
;   (defn outer-macro
;     [outer-arg-1 outer-arg-2]
;     (precept.dsl-ns/outer-macro outer-arg-1 outer-arg-2)))
;
;#?(:cljs
;   (defn macro-context [other-macros-cljs]
;     (precept.dsl-ns/macro-context other-macros-cljs)))
