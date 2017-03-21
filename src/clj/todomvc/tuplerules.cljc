(ns todomvc.tuplerules
    #?(:clj
       (:require [todomvc.macros :refer [rewrite-lhs]]
                 [clara.rules.dsl :as dsl]
                 [clara.rules.compiler :as com]
                 [clara.rules :refer [mk-session]]))
    #?(:cljs (:require-macros todomvc.tuplerules)))

;; This technique borrowed from Prismatic's schema library (via clara).
#?(:clj
    (defn compiling-cljs?
      "Return true if we are currently generating cljs code.  Useful because cljx does not
             provide a hook for conditional macro expansion."
      []
      (boolean
        (when-let [n (find-ns 'cljs.analyzer)]
          (when-let [v (ns-resolve n '*cljs-file*)]
            ;; We perform this require only if we are compiling ClojureScript
            ;; so non-ClojureScript users do not need to pull in
            ;; that dependency.
            (require 'clara.macros)
            (require 'todomvc.macros)
            @v)))))

#?(:clj
   (defmacro def-tuple-session
     [name & sources-and-options]
     (if (compiling-cljs?)
       `(todomvc.macros/def-tuple-session ~name ~@sources-and-options)
       `(def ~name (com/mk-session ~`['todomvc.util
                                      ~@sources-and-options
                                      :fact-type-fn ~'(fn [[e a v]] a)
                                      :ancestors-fn ~'(fn [type] [:all])])))))

#?(:clj
   (defmacro def-tuple-rule
     [name & body]
     (if (com/compiling-cljs?)
       `(todomvc.macros/def-tuple-rule ~name ~@body)
       (let [doc             (if (string? (first body)) (first body) nil)
             body            (if doc (rest body) body)
             properties      (if (map? (first body)) (first body) nil)
             definition      (if properties (rest body) body)
             {:keys [lhs rhs]} (dsl/split-lhs-rhs definition)
             lhs-detuplified (rewrite-lhs lhs)]
         ;(println "LHS in" lhs)
         (println "LHS out" lhs-detuplified)
         (when-not rhs
           (throw (ex-info (str "Invalid rule " name ". No RHS (missing =>?).")
                    {})))
         `(def ~(vary-meta name assoc :rule true :doc doc)
            (cond-> ~(dsl/parse-rule* lhs-detuplified rhs properties {} (meta &form))
              ~name (assoc :name ~(str (clojure.core/name (ns-name *ns*)) "/" (clojure.core/name name)))
              ~doc (assoc :doc ~doc)))))))

;;LHS ([[?e :todo/title _]] [:exists [:todo/done]])
;
;;LHS ([:todo/title [[e a v]] (= ?e e)] [:exists [:todo/done]])
;(rewrite-lhs '([[?e :todo/title _]] [:exists [:todo/done]]))
;
;;TODO. Test these also. SHould work and be equivalent since b0c3f1c082dde6019675cc24a5a0120ce9c544ac
;(macroexpand
;  '(def-tuple-rule my-tuple-rule
;     "Docstring!!"
;     [?todo <- [?e :todo/title _]]
;     [:exists [:todo/done]]
;     =>
;     (println "Hello!")))
;
;(macroexpand
;  '(def-tuple-rule my-tuple-rule
;     "Docstring!!"
;     [[?e :todo/title ?v]]
;     [[?e2 :todo/title ?v]]
;     [:exists [:todo/done]]
;     =>
;     (println "Hello!")))
;
;;(macroexpand
;;  '(defrule my-rule
;;     "Docstring!!"
;;     [:todo/title [[e a v]] (= ?e e) (= v ?v)]
;;     [:todo/title [[e a v]] (not= e ?e) (= ?v2 ?v)]
;;     [:exists [:todo/done]]
;;     =>
;;     (println "Hello!")))
;
;;(macroexpand
;;  '(defrule my-regular-rule
;;     "Docstring!!"
;;     [?todo <- :todo/title [[e a v]] (= ?e e)]
;;     [:exists [:todo/done]]
;;     =>
;;     (println "Hello!")))
;
;;TODO. Test these. Should be equivalent as of 23814274a376f12535c455a55ed9d5e85c81f5c9
;(macroexpand
;  '(def-tuple-rule my-tuple-rule
;     "Docstring!!"
;     [[?e :todo/title _]]
;     [:exists [:todo/done]]
;     =>
;     (println "Hello!")))
;
;;(macroexpand
;;  '(defrule my-regular-rule
;;     "Docstring!!"
;;     [:todo/title [[e a v]] (= ?e e)]
;;     [:exists [:todo/done]]
;;     =>
;;     (println "Hello!")))
;;
;;
;;(macroexpand
;;  '(defrule my-rule
;;     [:todo/title [[e a v]] (= e? e)]
;;     =>
;;     (println "Hello!")))
;
