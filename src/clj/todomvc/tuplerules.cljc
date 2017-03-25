(ns todomvc.tuplerules
    #?(:clj
       (:require [todomvc.macros :refer [rewrite-lhs insert-each-logical]]
                 [clara.rules.dsl :as dsl]
                 [clara.rules.compiler :as com]
                 [clara.rules :refer [mk-session]]))
    #?(:cljs (:require-macros todomvc.tuplerules)))

(defn printmac [x & args]
  (comment (println x args)))

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
         (printmac "LHS before" lhs)
         (printmac "LHS after" lhs-detuplified)
         (when-not rhs
           (throw (ex-info (str "Invalid rule " name ". No RHS (missing =>?).")
                    {})))
         `(def ~(vary-meta name assoc :rule true :doc doc)
            (cond-> ~(dsl/parse-rule* lhs-detuplified rhs properties {} (meta &form))
              ~name (assoc :name ~(str (clojure.core/name (ns-name *ns*)) "/" (clojure.core/name name)))
              ~doc (assoc :doc ~doc)))))))

#?(:clj
   (defmacro def-tuple-query
     [name & body]
     (if (com/compiling-cljs?)
       `(todomvc.macros/def-tuple-query ~name ~@body)
       (let [doc (if (string? (first body)) (first body) nil)
             binding (if doc (second body) (first body))
             definition (if doc (drop 2 body) (rest body))
             rw-lhs (rewrite-lhs definition)]
         `(def ~(vary-meta name assoc :query true :doc doc)
            (cond-> ~(dsl/parse-query* binding rw-lhs {} (meta &form))
              ~name (assoc :name ~(str (clojure.core/name (ns-name *ns*)) "/" (clojure.core/name name)))
              ~doc (assoc :doc ~doc)))))))

(defn support-attr [str]
  (keyword "tuplerules.support" (name str)))

(defn veto-attr [str]
  (keyword "tuplerules.veto" (name str)))

(defn interns->set [interns]
  (into #{} (map str (keys interns))))

(defn write-decider [name fact interns]
  (let [interns-set    (interns->set interns)
        namestr        (str name)
        decider-name   (str namestr "__decider")
        already-exists (interns-set decider-name)]
    (if already-exists
      nil
      '(def-tuple-rule (symbol decider-name)
         [:exists [?e (support-attr namestr) ?fact]]
         [:not [?e (veto-attr namestr)]]
         =>
         (insert! fact)))))

(defn next-name [name peers]
  (if (empty? peers)
    (str name "-1")
    (str name
      (inc
        (apply max
          (map #(Integer/parseInt (last (clojure.string/split % (re-pattern "\\-"))))
            peers))))))

(defn write-with-role [name role facts lhs interns]
  (let [attr (if (= (name role) "support") (support-attr name) (veto-attr name))
        interns-set (interns->set interns)
        peers (filter #(clojure.string/includes? % (str name)) interns-set)
        rule-name (symbol (next-name name peers))
        foo (println "Name" interns-set)]
    `(clara.rules/defrule ~rule-name
       ~@lhs
       ~'=>
       ~`(~'insert! [~'?e ~attr ~facts]))))

#?(:clj
   (defmacro deflogical
     [name & body]
     (if (compiling-cljs?)
       `(todomvc.macros/deflogical ~name ~@body)
       (let [doc         (if (string? (first body)) (first body) nil)
             body        (if doc (rest body) body)
             properties  (if (map? (first body)) (first body) nil)
             role        (if (keyword? (first body)) (first body) nil)
             definition  (if role (rest body) body)
             facts       (first definition)
             condition   (rest definition)
             lhs         (rewrite-lhs condition)
             ;rhs         (insert-each-logical facts)
             interns     (println "Interns " (ns-interns *ns*))
             role-rule   (write-with-role name role facts lhs interns)
             decider     (write-decider name facts interns)
             name-exists (contains? (ns-interns *ns*) name)]
         ;(println "Role rule" `~role-rule)
         ;(println "Decider" decider)
         (if (nil? role)
           nil ;;roleless? first version
           (if decider
             role-rule
             role-rule))))))
           ;(if decider
           ;  (list role-rule decider)
           ;  role-rule)))))

           ;(list
           ;  `(def ~(vary-meta name assoc :rule true :doc doc)
           ;     (cond-> ~(dsl/parse-rule* lhs rhs properties {} (meta &form))
           ;       ~name (assoc :name ~(str (clojure.core/name (ns-name *ns*)) "/" (clojure.core/name name)))
           ;       ~doc (assoc :doc ~doc)))
           ;  `(def ~(vary-meta name assoc :rule true :doc doc)
           ;     (cond-> ~(dsl/parse-rule* lhs rhs properties {} (meta &form))
           ;       ~name (assoc :name ~(str (clojure.core/name (ns-name *ns*)) "/" (clojure.core/name
           ;                                                                         'my-dynamic-rule)))
           ;       ~doc (assoc :doc ~doc)))))))))
#?(:clj
   (defmacro deflogical%
     [name & body]
     (if (compiling-cljs?)
       `(todomvc.macros/deflogical ~name ~@body)
       (let [doc         (if (string? (first body)) (first body) nil)
             body        (if doc (rest body) body)
             properties  (if (map? (first body)) (first body) nil)
             definition  (if properties (rest body) body)
             facts (first definition)
             condition (rest definition)
             lhs (rewrite-lhs condition)
             rhs (insert-each-logical facts)]
         `(def ~(vary-meta name assoc :rule true :doc doc)
            (cond-> ~(dsl/parse-rule* lhs rhs properties {} (meta &form))
              ~name (assoc :name ~(str (clojure.core/name (ns-name *ns*)) "/" (clojure.core/name name)))
              ~doc (assoc :doc ~doc)))))))
