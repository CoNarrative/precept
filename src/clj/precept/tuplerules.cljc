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
                 [clara.rules.compiler :as com]))

     #?(:cljs (:require [precept.spec.sub :as sub]))
     #?(:cljs (:require-macros precept.tuplerules)))

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
            (require 'precept.dsl)
            (require 'precept.impl.rules)
            @v)))))

#?(:clj
   (defmacro def-tuple-session
     "Defines a session.

     (def-tuple-session my-session 'my-proj/my-ns :schema my-schema)

     Accepts same arguments as Clara's defsession plus an additional :schema option. Rules and
     queries are loaded from the provided namespace.

     :schema - A Datomic schema. Attributes with defined cardinality and uniqueness are be
     maintained at insertion time. Unlike Datomic, facts that are :db.unique/value and
     :db.unique/identity attributes are both upserted.

     Default options:
       :fact-type-fn - :a
         Tells Clara to index fact-types by the attribute slot of provided eav tuples
       :ancestors-fn - util/make-ancestors-fn
         If :schema provided, returns a function with the provided Datomic schema. Else called
         with no arguments in which case all facts will be treated as cardinality :one-to-one.
       :activation-group-fn - (util/make-activation-group-fn :calc)
          Orders rules by :group, :salience, and :super propertes given as first argument to
          def-tuple-rule. Rules marked :super are active across all groups. :salience determines
          precedence within the same group.
       :activation-group-sort-fn - (util/make-activation-group-fn [:action :calc :report :cleanup])
         Sets group order. Rules with :group property :action fire first, :calc second, and so on."
     [name & sources-and-options]
     (if (compiling-cljs?)
       `(precept.macros/def-tuple-session ~name ~@sources-and-options)
       (let [sources (take-while (complement keyword?) sources-and-options)
             options-in (apply hash-map (drop-while (complement keyword?) sources-and-options))
             impl-sources `['precept.impl.rules]
             hierarchy (if (:schema options-in)
                         `(schema/schema->hierarchy (concat ~(:schema options-in)
                                                           ~precept.schema/precept-schema))
                         `(schema/schema->hierarchy ~precept.schema/precept-schema))
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
     "Defines a rule.

     (def-tuple-rule my-rule
       {:group :action}
       [[_ :my-fact ?v]]
       =>
       (insert! [(guid) :my-other-fact ?v])

       Behaves identically to Clara's defrule. Supports positional syntax for 4-arity
       [e a v fact-id] tuples."
     (if (compiling-cljs?)
       `(precept.macros/def-tuple-rule ~name ~@body)
       (let [doc             (if (string? (first body)) (first body) nil)
             body            (if doc (rest body) body)
             properties      (if (map? (first body)) (first body) nil)
             definition      (if properties (rest body) body)
             {:keys [lhs rhs]} (dsl/split-lhs-rhs definition)
             lhs-detuplified (seq (macros/rewrite-lhs lhs rhs {:props properties :name name}))]
         (when-not rhs
           (throw (ex-info (str "Invalid rule " name ". No RHS (missing =>?).")
                    {})))
         (core/register-rule "rule" lhs rhs)
         (if (not (map? (first lhs-detuplified)))
           `(def ~(vary-meta name assoc :rule true :doc doc)
              (cond-> ~(dsl/parse-rule* lhs-detuplified rhs properties {} (meta &form))
                ~name (assoc :name ~(str (clojure.core/name (ns-name *ns*)) "/" (clojure.core/name name)))
                ~doc (assoc :doc ~doc)))
           `(do
              ~@(for [{:keys [name lhs rhs]} lhs-detuplified]
                  `(def
                     ~(vary-meta name assoc :rule true :doc doc)
                     (cond-> ~(dsl/parse-rule* lhs rhs properties {} (meta &form))
                       ~name (assoc :name ~(str (clojure.core/name (ns-name *ns*)) "/"
                                             (clojure.core/name name)))
                       ~doc (assoc :doc ~doc))))))))))


#?(:clj
   (defmacro def-tuple-query
     "Clara's defquery with precept DSL.

     (def-tuple-query my-query [:v]
       [?fact <- [_ :my-fact ?v]])

      Defines a named query that can be called with Clara's `query` function with optional
      arguments."
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
     "Prolog-style rule.

     (deflogical [?e :derived-fact ?v] :- [[?e :my-fact ?v]])

     Head/consequence is declared first followed by body/conditions.
     Uses :- as separator. Name is auto-generated. Auto-assigned to default activation group.
     Inserts are always logical.
     Does not support non-DSL syntax (e.g. println, let)."
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
   (defmacro defsub
     "Defines subscription response.

     (defsub :my-sub-name
       [[_ :my-fact ?v]]
       =>
       {:my-fact ?v}

       sub-name - keyword of subscription registered with `subscribe`
       LHS - any valid LHS syntax
       RHS - a hash-map to be passed to subscribers of sub-name"
     [kw & body]
     (if (compiling-cljs?)
       `(precept.macros/defsub ~kw ~@body)
       (let [name (symbol (str (name kw) "-sub___impl"))
             doc         (if (string? (first body)) (first body) nil)
             body        (if doc (rest body) body)
             properties  (if (map? (first body)) (first body) nil)
             definition  (if properties (rest body) body)
             {:keys [lhs rhs]} (dsl/split-lhs-rhs definition)
             sub-match `[::sub/request (~'= ~'?e___sub___impl ~'(:e this)) (~'= ~kw ~'(:v this))]
             map-only? (map? (first (rest rhs)))
             sub-map (if map-only? (first (rest rhs)) (last (last rhs)))
             rest-rhs (if map-only? nil (butlast (last rhs)))
             rw-lhs (conj (macros/rewrite-lhs lhs) sub-match)
             insertion `(util/insert! [~'?e___sub___impl ::sub/response ~sub-map])
             rw-rhs  (if map-only?
                       (list 'do insertion)
                       (list 'do (rest `(cons ~@rest-rhs ~insertion))))
             _ (core/register-rule "subscription" rw-lhs rw-rhs)]
         `(def ~(vary-meta name assoc :rule true :doc doc)
            (cond-> ~(dsl/parse-rule* rw-lhs rw-rhs {:group :report} {} (meta &form))
              ~name (assoc :name ~(str (clojure.core/name (ns-name *ns*)) "/" (clojure.core/name name)))
              ~doc (assoc :doc ~doc)))))))
