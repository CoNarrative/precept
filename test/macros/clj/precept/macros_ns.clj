(ns precept.macros-ns
    (:require [clara.rules.compiler :as com]
              [cljs.env :as env]
              [cljs.analyzer :as ana]))

;(defmacro get-productions [namespace]
  ;(let [_ (println "compiler env" @env/*compiler*)]
  ;(get-in @env/*compiler* [:clara.macros/productions namespace]))

(defmacro inner-macro [s]
  ;(let [_ (println "Productions" (get-productions (ns-name *ns*)))]
    `['(my-ns/my-fn) :from ['~s :all]])

(defmacro outer-macro [y xs]
  (let [_ (println "CLJS NS outerr macro" (com/cljs-ns))])
  `(into ['~y '~'<-] ~xs))

(def special-forms #{'inner-macro 'outer-macro})

(defn add-ns-if-special-form [x]
  (let [special-form? (special-forms x)]
    (if (list? x)
      (map add-ns-if-special-form x)
      (if special-form?
        (symbol (str "precept.macros-ns/" (name x)))
        x))))

(defmacro macro-context [other-macros]
  (let [_ (println "other-macros" other-macros)
        namespaced-specials (map add-ns-if-special-form other-macros)
        symbols (eval namespaced-specials)
        _ (println "CLJS NS" (com/cljs-ns))
        _ (println "Expanded in macro context" symbols)]
    {:result `(list '~symbols '~'+ '~'further-rearrangement)}))

;(defmacro some-macro [namespace]
;  (let [_ (println "Hi")] ;; in CLJS is output as code and cause error
  ;(let [v# @env/*compiler*]
        ;_ (vary-meta some-var assoc :hey "there")]
    ;`v#))
  ;(get-in @env/*compiler* [:clara.macros/productions namespace]))
    ;'(map identity ["some" "macro"])))

