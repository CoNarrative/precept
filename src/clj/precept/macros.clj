(ns precept.macros
    (:require [clara.rules.dsl :as cr-dsl]
              [clara.macros :as cm]
              [clara.rules.accumulators :as acc]
              [precept.core :as core]
              [precept.spec.lang :as lang]
              [precept.spec.factgen :as factgen]
              [precept.dsl :as dsl]
              [precept.spec.sub :as sub]
              [precept.util :as util]
              [precept.schema :as schema]
              [clojure.spec :as s]
              [clara.rules :as cr]))

(defn trace [& args]
  (comment (apply prn args)))

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
        body (into options (conj sources `'precept.impl.rules))]
    `(cm/defsession ~name ~@body)))


(def special-forms #{'<- 'entity})

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

(defn binding? [x]
  (trace "Is a binding?" x (s/valid? ::lang/variable-binding x))
  (s/valid? ::lang/variable-binding x))

(defn special-form? [x]
  (trace "Found special form " x)
  (s/valid? ::lang/special-form x))

(defn contains-rule-generator? [x]
  (trace "Found rule generator " x)
  (s/valid? ::lang/contains-rule-generator x))

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
  (let [outer-op (cr-dsl/ops (first expr))
        inner-op (cr-dsl/ops (first (second expr)))]
    (if inner-op
      (vector outer-op (vector inner-op
                         (if (= 1 (count (second (second expr)))) ;;attribute only
                           (second (second expr))
                           (parse-as-tuple (vector (second (second expr)))))))
      (vector outer-op (if (= 1 (count (second expr)))      ;;attribute only
                         (second expr)
                         (parse-as-tuple (vector (second expr))))))))


(defn rewrite-expr
  "Returns Clara DSL for single expression"
  [expr exprs idx]
  (let [leftmost        (first expr)
        op              (keyword? (cr-dsl/ops leftmost))
        fact-expression (and (not (keyword? leftmost))
                             (not (vector? leftmost))
                             (binding? leftmost))
        binding-to-type-only (and fact-expression
                                  (attr-only? (first (drop 2 expr))))
        has-accumulator (and (true? fact-expression)
                             (has-accumulator? (drop 2 expr)))
        is-test-expr (is-test-expr? leftmost)
        special-form (special-form? leftmost)
        cljs-namespace (clara.rules.compiler/cljs-ns)]
    (cond
      is-test-expr expr
      special-form (rewrite-expr (eval (map add-ns-if-special-form leftmost)) exprs idx)
      binding-to-type-only (fact-binding-with-type-only expr)
      op (parse-with-op expr)
      has-accumulator (parse-with-accumulator expr)
      fact-expression (parse-with-fact-expression expr)
      :else (parse-as-tuple expr))))


(defmacro foob [x] x)
;; TODO. pass in name, salience so we can generate rules with salience relative
;; to subject and access name here. Also RHS....basically everything
(defn generate-rules [expr exprs idx props rhs]
  (let [var-binding (:join (:gen expr))
        nom (:name props)
        ;; This assumes the binding we're looking for exists in accumulator syntax
        matching-expr (first (filter #(and (has-accumulator? (drop 2 %))
                                        (= var-binding (first %))
                                        (> idx (.indexOf exprs %)))
                               exprs))
        lhs-mod (assoc (vec exprs) idx (parse-as-tuple`[[~'_ ::factgen/response ~var-binding]]))]
    [{:name (symbol (str nom (-> expr :gen :name-suffix)))
      :lhs (parse-with-accumulator matching-expr)
      :rhs `(let [req-id# (precept.util/guid)]
              (precept.util/insert! [[req-id# ::factgen/request :entities]
                                     [req-id# :entities/order ~var-binding]])
              (doseq [eid# ~var-binding]
                (precept.util/insert! [req-id# :entities/eid eid#])))}
     {:name nom
      :lhs (seq lhs-mod)
      :rhs rhs}]))

(foob
  (generate-rules
    (dsl/entities ?eids)
    '([?eids <- (acc/all :e) :from [:interesting-fact]]
      [[(<- ?interesting-facts (dsl/entities ?eids))]])
    1
    {:name 'hi}
    '(do nil)))





(defn rewrite-lhs
  "Returns Clara DSL for rule LHS"
  [exprs]
  (let [generative (first (filter contains-rule-generator? exprs))]
    (if generative
      (generate-rules generative exprs (.indexOf exprs generative))
      (map-indexed (fn [idx expr] (rewrite-expr expr exprs idx))
                   exprs))))

(defmacro def-tuple-rule
  "CLJS version of def-tuple-rule"
  [name & body]
  (let [doc         (if (string? (first body)) (first body) nil)
        body        (if doc (rest body) body)
        properties  (if (map? (first body)) (first body) nil)
        definition  (if properties (rest body) body)
        {:keys [lhs rhs]} (cr-dsl/split-lhs-rhs definition)
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
        rhs (list `(precept.util/insert! ~head))]
    `(cm/defrule ~name ~@lhs ~'=> ~@rhs)))

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
        sub-match `[::sub/request (~'= ~'?e___sub___impl ~'(:e this)) (~'= ~kw ~'(:v this))]
        map-only? (map? (first (rest rhs)))
        sub-map (if map-only? (first (rest rhs)) (last (last rhs)))
        rest-rhs (if map-only? nil (butlast (last rhs)))
        rw-lhs (conj (rewrite-lhs lhs) sub-match)
        insertion `(util/insert! [~'?e___sub___impl ::sub/response ~sub-map])
        rw-rhs  (if map-only?
                  (list insertion)
                  (list (rest `(cons ~@rest-rhs ~insertion))))
        _ (core/register-rule "subscription" rw-lhs rw-rhs)]
    `(cm/defrule ~name ~@passthrough ~@rw-lhs ~'=> ~@rw-rhs)))
