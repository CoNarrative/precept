(ns todomvc.macros
    (:require [clara.rules :refer [defrule]]
              [clara.rules.dsl :as dsl]
              [clara.macros :as cm]
              [clara.rules.compiler :as com]))

(defn printmac [x & args]
  (comment (println x args)))

(defmacro def-tuple-session
  "Wrapper around Clara's `defsession` macro.
  Preloads query helpers."
  [name & sources-and-options]
  `(clara.macros/defsession
     ~name
     'todomvc.util
     ~@sources-and-options
     :fact-type-fn ~'(fn [[e a v]] a)
     :ancestors-fn ~'(fn [type] [:all])))

;TODO. use .spec to define schema
(defn binding? [x]
  (printmac "Is a binding?" x)
  (printmac "passes " (try (= (first (name x)) \?)
                       (catch ClassCastException e
                         false)))

  (and
    (symbol? x)
    (= (first (name x)) \?)))

(defn sexpr? [x]
  (printmac "Is a sexpr?" x (and (list? x)))
  (list? x))

;TODO. use .spec to define schema
(defn value-expr? [x]
  (printmac "Is a value-expr?" x)
  (printmac "Type in value-expr test" (type x))
  (and
    (identity x)
    (not= '_ x)
    (not (binding? x))
    (not (sexpr? x))))

(defn has-accumulator? [expr]
  (printmac "Has accumulator ?" (sexpr? (first expr)))
  (and
    (sexpr? (first expr))
    (or (= (second expr) 'from)
        (= (second expr) :from))))

(defn variable-bindings [tuple]
  (into {}
    (filter (fn [[k v]] (binding? v))
      {:e (first tuple)
       :a (second tuple)
       :v (last tuple)})))

(defn sexprs-with-bindings [tuple]
  (into {}
    (filter (fn [[k v]] (sexpr? v))
      {:a (second tuple)
       :v (last tuple)})))

(defn positional-value [tuple]
  (into {}
    (filter (fn [[k v]] (value-expr? v))
      {:v (first (drop 2 tuple))})))

(defn parse-as-tuple [expr]
  (let [tuple                          (first expr)
        bindings                       (variable-bindings tuple)
        bindings-and-constraint-values (merge bindings
                                         (sexprs-with-bindings tuple)
                                         (positional-value tuple))
        value-expressions              (positional-value tuple)
        attribute                      (if (keyword? (second tuple)) (second tuple) :all)]
    (printmac "Tuple: " tuple)
    (printmac "Variable bindings for form:" bindings)
    (printmac "Value expressions for form" value-expressions)
    (printmac "With s-exprs merged:" bindings-and-constraint-values)
    (reduce
      (fn [rule-expr [eav v]]
        (printmac "K V" eav v)
        (conj rule-expr
          (if (sexpr? v)
            v
            (list '= v (symbol (name eav))))))
      (vector attribute (vector ['e 'a 'v]))
      bindings-and-constraint-values)))

(defn parse-with-fact-expression [expr]
  (let [fact-expression (take 2 expr)
        expression      (drop 2 expr)]
    (conj (lazy-seq (parse-as-tuple expression))
      (second fact-expression)
      (first fact-expression))))

(defn parse-with-accumulator [expr]
  (let [fact-expression (take 2 expr)
        accumulator      (take 2 (drop 2 expr))
        expression (drop 4 expr)]
    (vector
       (first fact-expression)
       (second fact-expression)
       (first accumulator)
       (second accumulator)
       (if-let [attr-only (= (count expression) 1)]
         (first expression)
         (parse-as-tuple expression)))))

(defn rewrite-lhs [exprs]
  (mapv (fn [expr]
          (let [leftmost        (first expr)
                op              (keyword? (dsl/ops leftmost))
                fact-expression (and (not (keyword? leftmost))
                                     (not (vector? leftmost))
                                     (binding? leftmost))
                has-accumulator (if (and (true? fact-expression)
                                         (has-accumulator? (drop 2 expr)))
                                    true
                                    nil)]
            (cond
              op expr
              has-accumulator (parse-with-accumulator expr)
              fact-expression (parse-with-fact-expression expr)
              :else (parse-as-tuple expr))))
    exprs))

(defmacro def-tuple-rule
  [name & body]
  (let [doc        (if (string? (first body)) (first body) nil)
        body       (if doc (rest body) body)
        properties (if (map? (first body)) (first body) nil)
        definition (if properties (rest body) body)
        {:keys [lhs rhs]} (dsl/split-lhs-rhs definition)
        rw-lhs    (reverse (into '() (rewrite-lhs lhs)))
        unwrite-rhs (rest rhs)
        rule `(~@rw-lhs ~'=> ~@unwrite-rhs)]
        ;test (printmac "test1" (dsl/split-lhs-rhs (conj (rest rhs) '=> (rest rw-lhs))))
        ;test2 (printmac "test2" (dsl/split-lhs-rhs rule))]
    ;(printmac "GUTS" rule)
    ;(printmac "rw-lhs" rw-lhs)
    `(cm/defrule ~name ~@rw-lhs ~'=> ~@unwrite-rhs)))
    ;`(cm/defrule ~name ~(list ~doc ~@rw-lhs ~'=> ~@unwrite-rhs))))

;(def-tuple-rule foo
;  [[_ :bar "hi"]]
;  [[_ :there 42]]
;  =>
;  (println "x")
;  (println "y"))

;(defmacro defaction
;  [name event effect & body]
;  `(defrule ~name
;     (conj `[:exists [event]] ~@body)
;    => (second effect)))
;
;(macroexpand
;  '(defaction foo
;     :ui/toggle-complete-action
;     [:effect [[?e :todo/done :tag]]]
;     [:todo/title [[e a v]] (= ?e e)]))
;


