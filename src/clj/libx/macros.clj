(ns libx.macros
    (:require [clara.rules :refer [defrule insert! fire-rules]]
              [clara.rules.dsl :as dsl]
              [clara.macros :as cm]
              [libx.spec.lang :as lang]
              [libx.util :refer [insert retract]]
              [clara.rules.compiler :as com]
              [clojure.spec :as s]))

(defn trace [& args] (comment (apply prn args)))

(defmacro def-tuple-session
  "Wrapper around Clara's `defsession` macro.
  Preloads query helpers."
  [name & sources-and-options]
  `(cm/defsession
     ~name
     ~@sources-and-options
     :fact-type-fn ~':a
     :ancestors-fn ~'(fn [type] [:all])))

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
       :v (last tuple)})))

(defn sexprs-with-bindings [tuple]
  (into {}
    (filter (comp sexpr? second)
      {:a (second tuple)
       :v (last tuple)})))

(defn positional-value [tuple]
 (let [v-position (first (drop 2 tuple))]
  (if (not (value-expr? v-position))
    {}
    {:v (list '= v-position '(:v this))})))

(defn fact-binding-with-type-only [expr]
  (let [fact-binding (take 2 expr)
        fact-type (if (keyword? (last expr)) (last expr) (first (last expr)))]
    `(~@fact-binding ~fact-type)))

(defn parse-as-tuple [expr]
  "Parses rule expression as if it contains just a tuple.
  Does not take tuple as input! [ [] ], not []"
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

(defn parse-with-fact-expression [expr]
  "Returns Clara DSL for `?binding <- [tuple]`"
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

(defn parse-with-op [expr]
  "Returns Clara DSL for `[:op x]`, [:op [:op x] where x is
  :keyword, [:keyword] or [tuple]"
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

(defn rewrite-lhs [exprs]
  "Returns Clara DSL"
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

;TODO. Pass docstring and properties to Clara's defrule
(defmacro def-tuple-rule
  "For CLJS"
  [name & body]
  (let [doc         (if (string? (first body)) (first body) nil)
        body        (if doc (rest body) body)
        properties  (if (map? (first body)) (first body) nil)
        definition  (if properties (rest body) body)
        {:keys [lhs rhs]} (dsl/split-lhs-rhs definition)
        rw-lhs      (rewrite-lhs lhs)
        passthrough (filter some? (list doc properties))
        unwrite-rhs (rest rhs)]
    `(cm/defrule ~name ~@passthrough ~@rw-lhs ~'=> ~@unwrite-rhs)))

(defmacro def-tuple-query
  "For CLJS"
  [name & body]
  (let [doc (if (string? (first body)) (first body) nil)
        binding (if doc (second body) (first body))
        definition (if doc (drop 2 body) (rest body))
        rw-lhs      (rewrite-lhs definition)
        passthrough (filter #(not (nil? %)) (list doc binding))]
    `(cm/defquery ~name ~@passthrough ~@rw-lhs)))

(defn insert-each-logical [facts]
  "Returns sequence of facts with insert! if seq of one or insert-all! if multiple"
  (println "facts" facts)
  (if (= (count facts) 1)
    `(do (~'insert! ~(first facts)))
    `(do (~'insert-all! ~facts))))

(defmacro deflogical
  [name & body]
  (let [doc         (if (string? (first body)) (first body) nil)
        body        (if doc (rest body) body)
        properties  (if (map? (first body)) (first body) nil)
        definition  (if properties (rest body) body)
        facts (first definition)
        condition (rest definition)
        lhs (rewrite-lhs condition)
        rhs (insert-each-logical facts)]
    `(cm/defrule ~name ~@lhs ~'=> ~@rhs)))
