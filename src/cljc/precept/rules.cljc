(ns precept.rules
    #?(:clj
       (:require [precept.core :as core]
                 [precept.macros :as macros]
                 [precept.schema :as schema]
                 [precept.spec.sub :as sub]
                 [precept.util :as util]
                 [clara.rules :as cr]
                 [clara.macros :as cm]
                 [clara.rules.dsl :as dsl]
                 [clara.rules.compiler :as com]
                 [precept.state :as state]
                 [clara.rules.compiler :as com]))

    #?(:cljs (:require [precept.spec.sub :as sub]
                       [precept.schema :as schema]
                       [precept.accumulators]
                       [precept.state :as state]))
    #?(:cljs (:require-macros precept.rules)))

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
            (require 'precept.schema)
            (require 'precept.accumulators)
            @v)))))

#?(:clj
   (defmacro session
     "Defines a session.

     (session my-session 'my-proj/my-ns :db-schema my-schema)

     Accepts same arguments as Clara's defsession plus :db-schema and :client-schema options.
     Rules and queries are loaded from the provided namespace. To load rules from multiple
     namespaces, a vector of namespaces may be provided.

     :db-schema - Datomic-format schema for persistent facts. Precept enforces cardinality and
       uniqueness similarly to Datomic. All facts are treated as non-unique and one-to-one
       cardinality by default.

     :client-schema - Datomic-format schema for non-perstent facts. Precept enforces cardinality
       and uniqueness for attributes in this schema the same way it does for :db-schema. It
       serves two main purposes. 1. To define client-side facts as one-to-many or unique. 2. To
       allow Precept's API to filter out facts that should not be persisted when writing to a
       database.

     Defaults:

     `:fact-type-fn` - `:a`
       Tells Clara to index facts by attribute. This is the preferred :fact-type-fn option for
       working with eav tuples.

     `:ancestors-fn` - `util/make-ancestors-fn`
       If :db-schema and/or :client-schema are provided, uses Clojure's `make-hierarchy` and
       `derive` functions to assign cardinality and uniqueness for each provided attribute.
       When no schemas are provided, all facts will be descendents of #{:all :one-to-one}.
       Because all facts descend from `:all`, rules written with the `:all` attribute can
       match facts independently of their attributes.

     `:activation-group-fn` - `(util/make-activation-group-fn :calc)`
        Allows categorization and prioritization of some rules over others. Puts a rule into a
        prioritization group according to the optional first argument to rule.
        Assigns the default values `{:group :calc :salience 0 :super false}` to rules where the
        without these arguments.
        argument to rule. :salience determines precedence within the same group.
        Rules marked :super are active across all groups.

     `:activation-group-sort-fn` - `(util/make-activation-group-fn [:action :calc :report :cleanup])`
       Determines the scheme by which some rules are given priority over others. Rules in the
       `:action` group will be given the chance to fire before rules in the `:calc` group and so on.
       When determining the priority of two rules are in the same group, the :salience property
       serves as a tiebreaker, with higher salience rules winning over lower salience ones."
     [name & sources-and-options]
     (if (compiling-cljs?)
       `(precept.macros/session ~name ~@sources-and-options)
       (let [sources (take-while (complement keyword?) sources-and-options)
             options-in (apply hash-map (drop-while (complement keyword?) sources-and-options))
             impl-sources `['precept.impl.rules]
             hierarchy `(schema/init! (select-keys ~options-in [:db-schema :client-schema]))
             ancestors-fn `(util/make-ancestors-fn ~hierarchy)
             defaults {:fact-type-fn :a
                       :ancestors-fn ancestors-fn
                       :activation-group-fn `(util/make-activation-group-fn ~core/default-group)
                       :activation-group-sort-fn `(util/make-activation-group-sort-fn
                                                   ~core/groups ~core/default-group)}
             options (mapcat identity (merge defaults (dissoc options-in :db-schema :client-schema)))
             body (into options (concat sources impl-sources))]
         `(def ~name (com/mk-session `~[~@body]))))))

#?(:clj
   (defmacro rule
     [name & body]
     "Defines a rule.

     (rule my-rule
       {:group :action}
       [[_ :my-fact ?v]]
       =>
       (insert! [(guid) :my-other-fact ?v])

       Behaves identically to Clara's defrule. Supports positional syntax for 4-arity
       [e a v fact-id] tuples."
     (if (compiling-cljs?)
       `(precept.macros/rule ~name ~@body)
       (let [doc             (if (string? (first body)) (first body) nil)
             body            (if doc (rest body) body)
             properties      (if (map? (first body)) (first body) nil)
             definition      (if properties (rest body) body)
             source-ns-name (ns-name *ns*)
             {:keys [lhs rhs]} (dsl/split-lhs-rhs definition)
             rule-defs (macros/get-rule-defs lhs rhs {:props properties :name name})
             _ (doseq [{:keys [name lhs rhs]} rule-defs]
                 (core/register-rule {:name name
                                      :ns source-ns-name
                                      :type "rule"
                                      :lhs lhs
                                      :rhs rhs}))]
         (when-not rhs (throw (ex-info (str "Invalid rule " name ". No RHS (missing =>?).") {})))
         `(do ~@(for [{:keys [name lhs rhs]} rule-defs]
                  `(def ~(vary-meta name assoc :rule true :doc doc)
                     (cond-> ~(dsl/parse-rule* lhs rhs properties {} (meta &form))
                       ~name (assoc :name ~(str (clojure.core/name source-ns-name) "/"
                                                (clojure.core/name name)))
                       ~doc (assoc :doc ~doc)))))))))

#?(:clj
   (defmacro defquery
     "Clara's defquery with precept DSL.

     (defquery my-query [:v]
       [?fact <- [_ :my-fact ?v]])

      Defines a named query that can be called with Clara's `query` function with optional
      arguments."
     [name & body]
     (if (compiling-cljs?)
       `(precept.macros/defquery ~name ~@body)
       (let [doc (if (string? (first body)) (first body) nil)
             binding (if doc (second body) (first body))
             definition (if doc (drop 2 body) (rest body))
             rw-lhs (reverse (into '() (macros/rewrite-lhs definition)))]
         (core/register-rule {:name name :ns *ns* :type "query" :lhs definition :rhs nil})
         `(def ~(vary-meta name assoc :query true :doc doc)
            (cond-> ~(dsl/parse-query* binding rw-lhs {} (meta &form))
              ~name (assoc :name ~(str (clojure.core/name (ns-name *ns*)) "/"
                                       (clojure.core/name name)))
              ~doc (assoc :doc ~doc)))))))

#?(:clj
   (defmacro define
     "Prolog-style rule.

     (define [?e :derived-fact ?v] :- [[?e :my-fact ?v]])

     Head/consequence is declared first followed by body/conditions.
     Uses :- as separator. Name is auto-generated. Auto-assigned to default activation group.
     Inserts are always logical.
     Does not support non-DSL syntax (e.g. println, let)."
     [& forms]
     (if (compiling-cljs?)
       `(precept.macros/define ~@forms)
       (let [{:keys [body head]} (util/split-head-body forms)
             properties nil
             doc nil
             lhs (macros/rewrite-lhs body)
             rhs `(do (precept.util/insert! ~head))
             name (core/register-rule
                    {:name nil
                     :type "define"
                     :ns (ns-name *ns*)
                     :lhs lhs
                     :rhs rhs
                     :consequent-facts head})]
         `(def ~(vary-meta name assoc :rule true :doc doc)
            (cond-> ~(dsl/parse-rule* lhs rhs properties {} (meta &form))
              ~name (assoc :name ~(str (clojure.core/name (ns-name *ns*)) "/"
                                       (clojure.core/name name)))
              ~doc (assoc :doc ~doc)))))))

#?(:clj
   (defmacro defsub
     "Defines subscription response.

     (defsub :my-sub-name
       [[_ :my-fact ?v]]
       =>
       {:my-fact ?v})

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
             sub-rhs (macros/parse-sub-rhs rhs)
             sub-cond `[[[~'?e___sub___impl ::sub/request ~kw]]]
             rule-defs (macros/get-rule-defs
                         (into lhs sub-cond)
                         sub-rhs {:name name :props properties})
             _ (doseq [{:keys [name lhs rhs]} rule-defs]
                 (core/register-rule {:name name
                                      :ns (ns-name *ns*)
                                      :type "subscription"
                                      :lhs lhs
                                      :rhs rhs}))]
         `(do ~@(for [{:keys [name lhs rhs]} rule-defs]
                  `(def ~(vary-meta name assoc :rule true :doc doc)
                     (cond-> ~(dsl/parse-rule* lhs rhs {:group :report} {} (meta &form))
                       ~name (assoc :name ~(str (clojure.core/name (ns-name *ns*)) "/"
                                                (clojure.core/name name)))
                       ~doc (assoc :doc ~doc)))))))))

(def fire-rules clara.rules/fire-rules)

(def q clara.rules/query)

#?(:clj
   (defn rules-in-ns
     [ns-sym]
     (let [rule-syms (map (comp symbol :name)
                       (filter #(= (:ns %) ns-sym)
                         (vals @state/rules)))]
       (set rule-syms)))

   :cljs
   (defn rules-in-ns
     [ns-sym]
     (let [rule-syms (map (comp symbol :name) @state/rules)]
       (set rule-syms))))

#?(:clj
    (defn unmap-all-rules
      "Usage: `(unmap-all-rules *ns*)`
              `(unmap-all-rules 'my-ns)`"
      [rule-ns]
      (let [registered-rules (rules-in-ns (ns-name rule-ns))]
        (doseq [[k v] (ns-interns rule-ns)]
          (when (contains? registered-rules k)
            (ns-unmap rule-ns k)))
        (do (reset! state/rules {})
            (ns-interns rule-ns)))))
