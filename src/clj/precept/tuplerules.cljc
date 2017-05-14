(ns precept.tuplerules
    #?(:clj
       (:require [precept.core :as core]
                 [precept.macros :as macros]
                 [precept.schema :as schema]
                 [precept.spec.sub :as sub]
                 [precept.util :as util]
                 [clara.rules :as cr]
                 [clara.macros :as cm]
                 [clara.rules.dsl :as dsl]
                 [clara.rules.compiler :as com])

       :cljs
       (:require [precept.spec.sub :as sub])
       (:require-macros precept.tuplerules)))

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
            (require 'precept.macros)
            (require 'precept.impl.rules)
            @v)))))

#?(:clj
   (defmacro def-tuple-session
     "Wraps Clara's defsession macro.
     Contains defaults for :fact-type-fn, :ancestors-fn, :activation-group-fn, :activation-group-sort-fn"
     [name & sources-and-options]
     (if (compiling-cljs?)
       `(precept.macros/def-tuple-session ~name ~@sources-and-options)
       (let [sources (take-while (complement keyword?) sources-and-options)
             options-in (apply hash-map (drop-while (complement keyword?) sources-and-options))
             impl-sources `['precept.impl.rules]
             hierarchy (if (:schema options-in)
                         `(schema/schema->hierarchy ~(:schema options-in))
                         nil)
             ancestors-fn (if hierarchy
                            `(util/make-ancestors-fn ~hierarchy)
                            `(util/make-ancestors-fn))
             defaults {:fact-type-fn :a
                       :ancestors-fn ancestors-fn
                       :activation-group-fn `(util/make-activation-group-fn ~core/default-group)
                       :activation-group-sort-fn `(util/make-activation-group-sort-fn
                                                   ~core/groups ~core/default-group)}
             options (mapcat identity (merge defaults (dissoc options-in :schema)))
             body (into options (concat sources impl-sources))]
         `(def ~name (com/mk-session `~[~@body]))))))

#?(:clj
   (defmacro def-tuple-rule
     [name & body]
     (if (compiling-cljs?)
       `(precept.macros/def-tuple-rule ~name ~@body)
       (let [doc             (if (string? (first body)) (first body) nil)
             body            (if doc (rest body) body)
             properties      (if (map? (first body)) (first body) nil)
             definition      (if properties (rest body) body)
             {:keys [lhs rhs]} (dsl/split-lhs-rhs definition)
             lhs-detuplified (reverse (into '() (macros/rewrite-lhs lhs)))]
         (when-not rhs
           (throw (ex-info (str "Invalid rule " name ". No RHS (missing =>?).")
                    {})))
         (core/register-rule "rule" lhs rhs)
         `(def ~(vary-meta name assoc :rule true :doc doc)
            (cond-> ~(dsl/parse-rule* lhs-detuplified rhs properties {} (meta &form))
              ~name (assoc :name ~(str (clojure.core/name (ns-name *ns*)) "/" (clojure.core/name name)))
              ~doc (assoc :doc ~doc)))))))

#?(:clj
   (defmacro def-tuple-query
     [name & body]
     (if (compiling-cljs?)
       `(precept.macros/def-tuple-query ~name ~@body)
       (let [doc (if (string? (first body)) (first body) nil)
             binding (if doc (second body) (first body))
             definition (if doc (drop 2 body) (rest body))
             rw-lhs (reverse (into '() (macros/rewrite-lhs definition)))]
         (core/register-rule "query" definition nil)
         `(def ~(vary-meta name assoc :query true :doc doc)
            (cond-> ~(dsl/parse-query* binding rw-lhs {} (meta &form))
              ~name (assoc :name ~(str (clojure.core/name (ns-name *ns*)) "/" (clojure.core/name name)))
              ~doc (assoc :doc ~doc)))))))

#?(:clj
   (defmacro deflogical
     [& forms]
     (if (compiling-cljs?)
       `(precept.macros/deflogical ~@forms)
       (let [{:keys [body head]} (util/split-head-body forms)
             properties nil
             doc nil
             name (symbol (core/register-rule "deflogical" body head))
             lhs (macros/rewrite-lhs body)
             rhs `(do (precept.util/insert! ~head))]
         `(def ~(vary-meta name assoc :rule true :doc doc)
            (cond-> ~(dsl/parse-rule* lhs rhs properties {} (meta &form))
              ~name (assoc :name ~(str (clojure.core/name (ns-name *ns*)) "/" (clojure.core/name name)))
              ~doc (assoc :doc ~doc)))))))

#?(:clj
    (defmacro store-action
      [a]
      (if (compiling-cljs?)
        `(precept.macros/store-action ~a)
        (let [name (symbol (str "action-handler-" (clojure.string/replace (subs (str a) 1) \/ \*)))
              doc nil
              properties {:group :action}
              lhs (list `[~a (~'= ~'?v ~'(:v this))])
              rhs `(do (precept.util/action-insert! ~'?v))]
          (core/register-rule "action-handler" a :default)
          `(def ~(vary-meta name assoc :rule true :doc doc)
             (cond-> ~(dsl/parse-rule* lhs rhs properties {} (meta &form))
               ~name (assoc :name ~(str (clojure.core/name (ns-name *ns*)) "/" (clojure.core/name name)))
               ~doc (assoc :doc ~doc)))))))
#?(:clj
    (defmacro defsub
      [kw & body]
      (if (compiling-cljs?)
        `(precept.macros/defsub ~kw ~@body)
        (let [name (symbol (str (name kw) "-sub___impl"))
              doc         (if (string? (first body)) (first body) nil)
              body        (if doc (rest body) body)
              properties  (if (map? (first body)) (first body) nil)
              definition  (if properties (rest body) body)
              {:keys [lhs rhs]} (dsl/split-lhs-rhs definition)
              sub-match `[::sub/request (~'= ~'?e ~'(:e this)) (~'= ~kw ~'(:v this))]
              rw-lhs      (conj (macros/rewrite-lhs lhs) sub-match)
              unwrite-rhs (drop-while #(not (map? %)) rw-lhs)
              rw-rhs `(do (util/insert! [~'?e ::sub/response ~(first (rest rhs))]))]
          (core/register-rule "subscription" rw-lhs rw-rhs)
          `(def ~(vary-meta name assoc :rule true :doc doc)
             (cond-> ~(dsl/parse-rule* rw-lhs rw-rhs {:group :report} {} (meta &form))
               ~name (assoc :name ~(str (clojure.core/name (ns-name *ns*)) "/" (clojure.core/name name)))
               ~doc (assoc :doc ~doc)))))))



