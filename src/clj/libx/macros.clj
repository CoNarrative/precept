(ns libx.macros
    (:require [clara.rules.compiler :as com]
              [clara.rules.dsl :as dsl]
              [clara.macros :as cm]
              [libx.spec.lang :as lang]
              [libx.util :as util]
              [libx.schema :as schema]
              [clojure.spec :as s]
              [libx.core :as core]
              [clara.rules :as cr]))

(defn trace [& args] (comment (apply prn args)))

(defmacro def-tuple-session
  "For CLJS. Wraps Clara's `defsession` macro."
  [name & sources-and-options]
  (let [sources (take-while (complement keyword?) sources-and-options)
        options-in (apply hash-map (drop-while (complement keyword?) sources-and-options))
        ancestors-fn (if (:schema options-in)
                       `(util/make-ancestors-fn (schema/schema->hierarchy ~(:schema options-in)))
                       `(util/make-ancestors-fn))
        options (mapcat identity
                 (merge {:fact-type-fn :a
                         :ancestors-fn ancestors-fn
                         :activation-group-fn `(util/make-activation-group-fn ~core/default-group)
                         :activation-group-sort-fn `(util/make-activation-group-sort-fn
                                                      ~core/groups ~core/default-group)}
                   (dissoc options-in :schema)))
        body (into options (conj sources `'libx.impl.rules))]
    `(cm/defsession ~name ~@body)))

(defn attr-only? [x]
  (trace "Attr only?" x (s/valid? ::lang/attribute-matcher x))
  (s/valid? ::lang/attribute-matcher x))

(defn binding? [x]
  (trace "Is a binding?" x (s/valid? ::lang/variable-binding x))
  (s/valid? ::lang/variable-binding x))

(defn sexpr? [x]
  (s/valid? ::lang/s-expr x))

(defn is-test-expr? [x]
  (s/valid? ::lang/test-expr x))

(defn value-expr? [x]
  (trace "Is a value-expr?" x (s/valid? ::lang/value-equals-matcher x))
  (s/valid? ::lang/value-equals-matcher x))

(defn has-accumulator? [expr]
  (trace "Has accumulator?" expr (s/valid? ::lang/accum-expr expr))
  (s/valid? ::lang/accum-expr expr))

(defn variable-bindings [tuple]
  (trace "Getting variable bindings for " tuple)
  (into {}
    (filter (comp binding? second)
      {:e (first tuple)
       :a (second tuple)
       :v (nth tuple 2 nil)
       :t (nth tuple 3 nil)})))

(defn sexprs-with-bindings
  "[(:id ?v) :foo 'bar'] -> (= (:id ?v) (:e this))"
  [tuple]
  (reduce
    (fn [acc [k v]]
      (if (and (sexpr? v) (some binding? (flatten v)))
        (assoc acc k (list '= v `(~k ~'this)))
        acc))
    {}
    {:e (first tuple)
     :a (second tuple)
     :v (last tuple)}))

(defn positional-value [tuple]
 (let [match-v (first (drop 2 tuple))
       match-tx (nth tuple 3 nil)]
   (reduce
     (fn [acc [k v]]
       (if (value-expr? v)
         (assoc acc k (list '= v `(~k ~'this)))
         acc))
     {}
     {:v match-v :t match-tx})))

(defn fact-binding-with-type-only [expr]
  (let [fact-binding (take 2 expr)
        fact-type (if (keyword? (last expr)) (last expr) (first (last expr)))]
    `(~@fact-binding ~fact-type)))

(defn parse-as-tuple
  "Parses rule expression as if it contains just a tuple.
  Does not take tuple as input! [ [] ], not []"
  [expr]
  (let [tuple                          (first expr)
        bindings                       (variable-bindings tuple)
        bindings-and-constraint-values (merge bindings
                                         (sexprs-with-bindings tuple)
                                         (positional-value tuple))
        attribute                      (if (keyword? (second tuple)) (second tuple) :all)]
    (trace "Tuple: " tuple)
    (trace "Variable bindings for form:" bindings)
    (trace "Value expressions for form" (positional-value tuple))
    (trace "With s-exprs merged:" bindings-and-constraint-values)
    (reduce
      (fn [rule-expr [eav v]]
        (trace "K V" eav v)
        (conj rule-expr
          (if (sexpr? v)
            v
            (list '= v
              (list (keyword (name eav)) 'this)))))
      (vector attribute)
      bindings-and-constraint-values)))

(defn parse-with-fact-expression
  "Returns Clara DSL for `?binding <- [tuple]`"
  [expr]
  (let [fact-expression (take 2 expr)
        expression      (drop 2 expr)]
    (conj (lazy-seq (parse-as-tuple expression))
      (second fact-expression)
      (first fact-expression))))

(defn parse-with-accumulator [expr]
  "Returns Clara DSL for `?binding <- (acc/foo) from [tuple]`"
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
          (parse-as-tuple expression)))))

(defn parse-with-op
  "Returns Clara DSL for `[:op x]`, [:op [:op x] where x is
  :keyword, [:keyword] or [tuple]"
  [expr]
  (let [outer-op (dsl/ops (first expr))
        inner-op (dsl/ops (first (second expr)))]
    (if inner-op
      (vector outer-op (vector inner-op
                         (if (= 1 (count (second (second expr)))) ;;attribute only
                           (second (second expr))
                           (parse-as-tuple (vector (second (second expr)))))))
      (vector outer-op (if (= 1 (count (second expr)))      ;;attribute only
                         (second expr)
                         (parse-as-tuple (vector (second expr))))))))

(defn rewrite-lhs
  "Returns Clara DSL"
  [exprs]
  (map (fn [expr]
          (let [leftmost        (first expr)
                op              (keyword? (dsl/ops leftmost))
                fact-expression (and (not (keyword? leftmost))
                                     (not (vector? leftmost))
                                     (binding? leftmost))
                binding-to-type-only (and fact-expression
                                          (attr-only? (first (drop 2 expr))))
                has-accumulator (and (true? fact-expression)
                                     (has-accumulator? (drop 2 expr)))
                is-test-expr (is-test-expr? leftmost)]
            (cond
              is-test-expr expr
              binding-to-type-only (fact-binding-with-type-only expr)
              op (parse-with-op expr)
              has-accumulator (parse-with-accumulator expr)
              fact-expression (parse-with-fact-expression expr)
              :else (parse-as-tuple expr))))
    exprs))

(defmacro def-tuple-rule
  "CLJS version of def-tuple-rule"
  [name & body]
  (let [doc         (if (string? (first body)) (first body) nil)
        body        (if doc (rest body) body)
        properties  (if (map? (first body)) (first body) nil)
        definition  (if properties (rest body) body)
        {:keys [lhs rhs]} (dsl/split-lhs-rhs definition)
        rw-lhs      (rewrite-lhs lhs)
        passthrough (filter some? (list doc properties))
        unwrite-rhs (rest rhs)]
    (core/register-rule "rule" lhs rhs)
    `(cm/defrule ~name ~@passthrough ~@rw-lhs ~'=> ~@unwrite-rhs)))

(defmacro def-tuple-query
  "CLJS version of def-tuple-query"
  [name & body]
  (let [doc (if (string? (first body)) (first body) nil)
        binding (if doc (second body) (first body))
        definition (if doc (drop 2 body) (rest body))
        rw-lhs      (rewrite-lhs definition)
        passthrough (filter #(not (nil? %)) (list doc binding))]
    (core/register-rule "query" definition nil)
    `(cm/defquery ~name ~@passthrough ~@rw-lhs)))

(defmacro deflogical
  "CLJS version of deflogical"
  [& forms]
  (let [{:keys [body head]} (util/split-head-body forms)
        name (symbol (core/register-rule "deflogical" body head))
        lhs (rewrite-lhs body)
        rhs (list `(libx.util/insert! ~head))]
    `(cm/defrule ~name ~@lhs ~'=> ~@rhs)))

(defmacro store-action
  "CLJS version of store-action"
  [a]
  (let [name (symbol (str "action-handler-" (clojure.string/replace (subs (str a) 1) \/ \*)))
        doc nil
        properties {:group :action}
        lhs (list `[~a (~'= ~'?v ~'(:v this))])
        rhs (list `(util/action-insert! ~'?v))]
    (core/register-rule "action-handler" a :default)
    `(cm/defrule ~name ~properties ~@lhs ~'=> ~@rhs)))

