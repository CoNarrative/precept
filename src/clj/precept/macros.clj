(ns precept.macros
    (:require [clara.macros :as cm]
              [clara.rules :as cr]
              [clara.rules.accumulators :as acc]
              [clara.rules.compiler :as com]
              [clara.rules.dsl :as cr-dsl]
              [cljs.env :as env]
              [clojure.spec.alpha :as s]
              [precept.core :as core]
              [precept.dsl :as dsl]
              [precept.util :as util]
              [precept.schema :as schema]
              [precept.spec.core :refer [conform-or-nil]]
              [precept.spec.lang :as lang]
              [precept.spec.rulegen :as rulegen]
              [precept.spec.sub :as sub]
              [precept.state :as state]))

(defn trace [& args]
  (comment (apply prn args)))

(defn store-session-def-in-compiler!
  "Stores session definition in cljs.env/*compiler*. May be accessed to recreate a session with
  identical name, arguments."
  [session-def]
  (swap! env/*compiler* update :precept.macros/session-defs
    (fn [x] (set (conj x session-def)))))

(defn existing-session-def
  "Returns session definition hash-map matching session-name from registry in compiler-env or nil
  if no match found"
  [cenv session-name]
  (let [session-defs (get @cenv :precept.macros/session-defs)]
      (first (filter #(= session-name (:name %)) session-defs))))

(defn options-map
  "Returns arguments to `session` minus rule sources as hash-map."
  [sources-and-options]
  (apply hash-map (drop-while (complement keyword?) sources-and-options)))

(defn sources-list
  "Returns rule sources from arguments to `session` as list."
  [sources-and-options]
  (take-while (complement keyword?) sources-and-options))

(defn merge-default-options
  [options-in ancestors-fn]
  (merge {:fact-type-fn :a
          :ancestors-fn ancestors-fn
          :activation-group-fn `(util/make-activation-group-fn ~core/default-group)
          :activation-group-sort-fn `(util/make-activation-group-sort-fn
                                       ~core/groups ~core/default-group)}
    options-in))

(def precept-options-keys [:db-schema :client-schema :reload])

(defmacro session
  "For CLJS. Wraps Clara's `defsession` macro."
  ([m]
   (let [name (:name m)
         body (:body m)]
     `(precept.macros/session ~name ~@body)))
  ([name & sources-and-options]
   (let [options (options-map sources-and-options)
         existing-def (existing-session-def env/*compiler* name)
         reloading? (and (:reload options) (seq existing-def))]
     (if reloading?
       `(def ~name (precept.repl/reload-session-cljs! '~name))
       `(precept.macros/session* ~name ~@sources-and-options)))))

(defn precept->clara-options
  [precept-options-map precept-options-keys]
  (mapcat identity (apply dissoc precept-options-map precept-options-keys)))

(defmacro session*
  ([m]
   (let [name (:name m)
         body (:body m)]
     `(precept.macros/session* ~name ~@body)))
  ([name & sources-and-options]
   (let [sources (sources-list sources-and-options)
         options-in (options-map sources-and-options)
         hierarchy `(schema/init! (select-keys ~options-in [:db-schema :client-schema]))
         ancestors-fn `(util/make-ancestors-fn ~hierarchy)
         precept-options-map (merge-default-options options-in ancestors-fn)
         cr-options-list (precept->clara-options precept-options-map precept-options-keys)
         rule-nses (conj sources `'precept.impl.rules)
         cr-body (into cr-options-list rule-nses)
         interned-ns-name (com/cljs-ns)
         session-def-clj {:name name
                          :ns-name interned-ns-name
                          :body sources-and-options
                          :rule-nses rule-nses
                          :options precept-options-map}
         session-def `{:name '~name
                       :ns-name '~interned-ns-name
                       :body '~sources-and-options
                       :rule-nses '~rule-nses
                       :options '~precept-options-map}]
        (store-session-def-in-compiler! session-def-clj)
        `(do (swap! state/session-defs assoc '~name ~session-def)
             (cm/defsession ~name ~@cr-body)))))

(defn parse-sub-rhs [rhs]
  (let [map-only? (map? (first (rest rhs)))
        sub-map (if map-only? (first (rest rhs)) (last (last rhs)))
        rest-rhs (if map-only? nil (butlast (last rhs)))
        insertion `(util/insert! [~'?e___sub___impl ::sub/response ~sub-map])]
    (if map-only?
      (list 'do insertion)
      (list 'do (rest `(cons ~@rest-rhs ~insertion))))))

;;TODO. Duplicates what we have in spec
(def special-forms #{'<- 'entity 'entities})

(defn add-ns-if-special-form [x]
  (let [special-form? (special-forms x)]
    (if (list? x)
      (map add-ns-if-special-form x)
      (if special-form?
        (symbol (str "precept.dsl/" (name x)))
        x))))

(defn attr-only? [x]
  (trace "Attr only?" x (s/valid? ::lang/attribute-matcher x))
  (s/valid? ::lang/attribute-matcher x))

(defn mk-parse-cache
  ([] (atom {:variable-bindings #{}}))
  ([initial] (atom initial)))

(defn cache-binding! [cache x]
  (trace "Adding binding to cache" @cache x)
  (swap! cache update :variable-bindings conj x))

(defn binding?! [x cache]
  (trace "Is a binding?" x (s/valid? ::lang/variable-binding x))
  (if (s/valid? ::lang/variable-binding x)
    (do (cache-binding! cache x)
        true)
    false))

(defn special-form? [x]
  (s/valid? ::lang/special-form x))

(defn sexpr? [x]
  (s/valid? ::lang/s-expr x))

(defn test-expr? [x]
  (s/valid? ::lang/test-expr x))

(defn value-expr? [x]
  (trace "Is a value-expr?" x (s/valid? ::lang/value-equals-matcher x))
  (s/valid? ::lang/value-equals-matcher x))

(defn has-accumulator? [expr]
  (trace "Has accumulator?" expr (s/valid? ::lang/accum-expr expr))
  (s/valid? ::lang/accum-expr expr))

(defn parsed-sexpr? [xs]
  (and (vector? xs)
       (some #(s/valid? ::lang/s-expr %)  xs)))

(defn variable-bindings
  ([tuple] (variable-bindings tuple (mk-parse-cache)))
  ([tuple cache]
   (trace "Getting variable bindings for " tuple)
   (into {}
     (filter (comp #(binding?! % cache) second)
       {:e (first tuple)
        :a (second tuple)
        :v (nth tuple 2 nil)
        :t (nth tuple 3 nil)}))))

(defn existing-binding? [cache x]
  ((:variable-bindings @cache) x))

(defn parse-sexpr
  ([sexpr] (parse-sexpr sexpr (mk-parse-cache)))
  ([sexpr cache]
   (let [var-bindings (filter #(s/valid? ::lang/variable-binding %) sexpr)
         ;; If more than one new binding we should surface an error
         new-bindings (remove #(existing-binding? cache %) var-bindings)
         new-binding (first new-bindings)
         pred (first sexpr)
         existing-binding-or-value (remove #{new-binding pred} sexpr)
         new-binding-expr (list '= new-binding `(~:v ~'this))
         rw-sexpr (map #(if (= new-binding %) `(~:v ~'this) %) sexpr)]
     (if (empty? new-bindings)
         sexpr
         (do (cache-binding! cache new-binding)
             (vector rw-sexpr new-binding-expr))))))


(defn sexprs-with-bindings
  "[(:id ?v) :foo 'bar'] -> [:foo (= (:id ?v) (:e this))
  [?e :foo (> 42 ?v)] -> [:foo (= ?v (:v this)) (> 42 (:v this))]"
  ([tuple] (sexprs-with-bindings tuple (mk-parse-cache)))
  ([tuple cache]
   (reduce
     (fn [acc [k v]]
       (cond
         (and (= k :v) (s/valid? ::lang/s-expr v)) (assoc acc k (parse-sexpr v cache))
         (and (sexpr? v) (some #(binding?! % cache)(flatten v))) (assoc acc k (list '= v `(~k ~'this)))
         :default acc))
     {}
     {:e (first tuple)
      :a (second tuple)
      :v (last tuple)})))

(defn positional-value [tuple]
 (let [match-e (nth tuple 0 nil)
       match-v (first (drop 2 tuple))
       match-tx (nth tuple 3 nil)]
   (reduce
     (fn [acc [k v]]
       (if (value-expr? v)
         (assoc acc k (list '= v `(~k ~'this)))
         acc))
     {}
     {:e match-e :v match-v :t match-tx})))

(defn fact-binding-with-type-only [expr]
  (let [fact-binding (take 2 expr)
        fact-type (if (keyword? (last expr)) (last expr) (first (last expr)))]
    `(~@fact-binding ~fact-type)))

(defn parse-as-tuple
  "Parses rule expression as if it contains just a tuple.
  Does not take tuple as input! [ [] ], not []"
  ([expr] (parse-as-tuple expr (mk-parse-cache)))
  ([expr cache]
   (let [tuple                          (first expr)
         bindings                       (variable-bindings tuple cache)
         bindings-and-constraint-values (merge bindings
                                         (sexprs-with-bindings tuple cache)
                                         (positional-value tuple))
         attribute                      (if (keyword? (second tuple)) (second tuple) :all)]
     (trace "Tuple: " tuple)
     (trace "Variable bindings for form:" bindings)
     (trace "Value expressions for form" (positional-value tuple))
     (trace "With s-exprs merged:" bindings-and-constraint-values)
     (reduce
       (fn [rule-expr [eav v]]
         (trace "K V" eav v)
         (cond
           (sexpr? v) (conj rule-expr v)
           (parsed-sexpr? v) (concat rule-expr v)
           :default (conj rule-expr (list '= v (list (keyword (name eav)) 'this)))))
       (vector attribute)
       bindings-and-constraint-values))))

(defn parse-with-fact-expression
  "Returns Clara DSL for `?binding <- [tuple]`"
  ([expr] (parse-with-fact-expression expr (mk-parse-cache)))
  ([expr cache]
   (let [fact-expression (take 2 expr)
         expression      (drop 2 expr)]
     (conj (lazy-seq (parse-as-tuple expression cache))
       (second fact-expression)
       (first fact-expression)))))

(defn parse-with-accumulator
  "Returns Clara DSL for `?binding <- (acc/foo) from [tuple]`"
  ([expr] (parse-with-accumulator expr (mk-parse-cache)))
  ([expr cache]
   (let [fact-expression (take 2 expr)
         accumulator     (take 2 (drop 2 expr))
         expression      (drop 4 expr)]
     (trace "To parse as tuple expr" expression)
     (vector
       (first fact-expression)
       (second fact-expression)
       (first accumulator)
       (second accumulator)
       (if (attr-only? (first expression))
           (first expression)
           (parse-as-tuple expression cache))))))

(defn parse-with-op
  "Returns Clara DSL for `[:op x]`, [:op [:op x] where x is
  :keyword, [:keyword] or [tuple]"
  ([expr] (parse-with-op expr (mk-parse-cache)))
  ([expr cache]
   (let [op (cr-dsl/ops (first expr))]
     (into [op]
       (if (attr-only? (second expr))
         (vector (second expr))
         (map
           (fn [x]
             (if (contains? cr-dsl/ops (first x))
               (parse-with-op x cache)
               (parse-as-tuple (vector x) cache)))
           (rest expr)))))))

(defn rewrite-expr
  "Returns Clara DSL for single expression"
  [expr cache]
  (let [leftmost        (first expr)
        op              (keyword? (cr-dsl/ops leftmost))
        fact-expression (and (not (keyword? leftmost))
                             (not (vector? leftmost))
                             (binding?! leftmost cache))
        binding-to-type-only (and fact-expression
                                  (attr-only? (first (drop 2 expr))))
        has-accumulator (and (true? fact-expression)
                             (has-accumulator? (drop 2 expr)))
        is-test-expr (test-expr? leftmost)
        special-form (special-form? leftmost)]
    (cond
      is-test-expr expr
      special-form (rewrite-expr (eval (map add-ns-if-special-form leftmost)) cache)
      binding-to-type-only (fact-binding-with-type-only expr)
      op (parse-with-op expr cache)
      has-accumulator (parse-with-accumulator expr cache)
      fact-expression (parse-with-fact-expression expr cache)
      :else (parse-as-tuple expr cache))))

(defn rewrite-lhs
  "Returns Clara DSL for rule LHS"
  [lhs]
  (let [cache (mk-parse-cache)]
    (map (fn [expr] (rewrite-expr expr cache)) lhs)))

(defn replace-at-index
  "Removes item at idx of coll and adds new list members (xs) starting at idx"
  [idx xs coll]
  (let [[as bs] (split-at idx coll)]
    (concat (concat as xs) (rest bs))))

;; TODO.
;; - Generate rules with salience relative to subject
;; - Add test
(defn generate-rules
  [expr idx lhs rhs props]
  (let [ast (eval (add-ns-if-special-form expr))
        var-binding (:join (:gen ast))
        fact-binding (second (first (nth lhs idx)))
        original-name (:name props)
        gen-name (symbol (str original-name (-> ast :gen :name-suffix)))
        ;; This assumes the binding we're looking for exists in accumulator syntax
        matching-expr (first (filter #(and (has-accumulator? (drop 2 %))
                                        (= var-binding (first %))
                                        (> idx (.indexOf lhs %)))
                               lhs))
        id (gensym "?")
        gen-conds (list [[id ::rulegen/for-rule original-name]]
                        [[id ::rulegen/for-macro :precept.spec.rulegen/entities]]
                        [[id ::rulegen/request-params var-binding]]
                        [[id ::rulegen/response fact-binding]])
        with-replaced-conditions (remove #{matching-expr} (replace-at-index idx gen-conds lhs))
        cache (mk-parse-cache)
        rw-lhs (map #(rewrite-expr % cache) with-replaced-conditions)
        req-id (precept.util/guid)]
    [{:name gen-name
      :lhs (list (parse-with-accumulator matching-expr))
      :rhs `(do
              (precept.util/insert!
                [[~req-id ::rulegen/for-rule ~original-name]
                 [~req-id ::rulegen/for-macro :precept.spec.rulegen/entities]
                 [~req-id ::rulegen/request-params ~var-binding]
                 [~req-id :precept.spec.rulegen.entities/order ~var-binding]])
              (doseq [eid# ~var-binding]
                (precept.util/insert! [~req-id :precept.spec.rulegen.entities/eid eid#])))}
     {:name original-name
      :lhs rw-lhs
      :rhs rhs}]))

(defn find-gen-in-lhs [lhs]
  (first
    (->> lhs
      (map-indexed
         (fn [idx expr]
           (let [form (s/conform ::lang/special-form (first expr))]
             (if (s/valid? ::lang/contains-rule-generator form)
               [idx (last form)]
               []))))
      (filter seq))))

(defn get-rule-defs [lhs rhs props]
  (let [[idx generative-expr] (find-gen-in-lhs lhs)]
    (if generative-expr
      (generate-rules generative-expr idx lhs rhs props)
      [{:name (:name props)
        :lhs (rewrite-lhs lhs)
        :rhs rhs}])))

(defmacro rule
  "CLJS version of rule"
  [name & body]
  (let [doc         (if (string? (first body)) (first body) nil)
        body        (if doc (rest body) body)
        properties  (if (map? (first body)) (first body) nil)
        definition  (if properties (rest body) body)
        {:keys [lhs rhs]} (cr-dsl/split-lhs-rhs definition)
        rule-defs      (get-rule-defs lhs rhs {:props properties :name name})
        passthrough (filter some? (list doc properties))
        unwrite-rhs (rest rhs)]
    `(do ~@(for [{:keys [name lhs rhs]} rule-defs]
              `(let [rule-data# {:name '~name
                                 :ns *ns*
                                 :type "rule"
                                 :lhs '~lhs
                                 :rhs '~rhs}]
                (do
                  (core/register-rule rule-data#)
                  (cm/defrule ~name ~@passthrough ~@lhs ~'=> (do ~rhs))))))))

(defmacro defquery
  "CLJS version of defquery"
  [name & body]
  (let [doc (if (string? (first body)) (first body) nil)
        binding (if doc (second body) (first body))
        definition (if doc (drop 2 body) (rest body))
        rw-lhs      (rewrite-lhs definition)
        passthrough (filter #(not (nil? %)) (list doc binding))]
    `(let [lhs# '~rw-lhs
           ns# *ns*]
       (do
         (core/register-rule
            {:name '~name :ns ns# :type "query" :lhs lhs# :rhs nil})
         (cm/defquery ~name ~@passthrough ~@rw-lhs)))))

(defmacro define
  "CLJS version of define"
  [& forms]
  (let [{:keys [body head]} (util/split-head-body forms)
        lhs (rewrite-lhs body)
        rhs (list `(precept.util/insert! ~head))
        ;; Call `register-rule` at compile time to get an autogenerated name for the rule via a
        ;; hash so we can pass to defrule. Call again at runtime to register the rule and have
        ;; the name match what was compiled
        name (core/register-rule
               {:name nil
                :type "define"
                :ns (ns-name *ns*)
                :lhs `'~lhs ;; must be quoted for hash equivalence between CLJS, CLJ
                :rhs rhs
                :consequent-facts head})]
    `(let [name# (core/register-rule
                     {:name nil
                      :type "define"
                      :ns nil
                      :lhs ''~lhs
                      :rhs '~rhs
                      :consequent-facts '~head})]
       (cm/defrule ~name ~@lhs ~'=> ~@rhs))))

(defmacro defsub
  [kw & body]
  "CLJS version of defsub"
  (let [name (symbol (str (name kw) "-sub___impl"))
        doc         (if (string? (first body)) (first body) nil)
        body        (if doc (rest body) body)
        properties  (if (map? (first body)) (first body) nil)
        definition  (if properties (rest body) body)
        passthrough (filter some? (list doc (merge {:group :report} properties)))
        {:keys [lhs rhs]} (cr-dsl/split-lhs-rhs definition)
        sub-rhs (parse-sub-rhs rhs)
        sub-cond `[[[~'?e___sub___impl ::sub/request ~kw]]]
        rule-defs (get-rule-defs (into lhs sub-cond) sub-rhs {:name name :props properties})]
      `(do ~@(for [{:keys [name lhs rhs]} rule-defs]
               `(let [rule-data# {:name '~name
                                  :ns *ns*
                                  :type "subscription"
                                  :lhs '~lhs
                                  :rhs '~rhs}]
                  (do
                    (core/register-rule rule-data#)
                    (cm/defrule ~name ~@passthrough ~@lhs ~'=> (do ~rhs))))))))
