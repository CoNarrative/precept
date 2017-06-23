(ns precept.macros
    (:require [clara.rules.dsl :as cr-dsl]
              [clara.macros :as cm]
              [clara.rules.accumulators :as acc]
              [precept.core :as core]
              [precept.spec.core :refer [conform-or-nil]]
              [precept.spec.lang :as lang]
              [precept.spec.rulegen :as rulegen]
              [precept.dsl :as dsl]
              [precept.spec.sub :as sub]
              [precept.util :as util]
              [precept.schema :as schema]
              [clojure.spec :as s]
              [clara.rules :as cr]))

(defn trace [& args]
  (comment (apply prn args)))

(defmacro session
  "For CLJS. Wraps Clara's `defsession` macro."
  [name & sources-and-options]
  (let [sources (take-while (complement keyword?) sources-and-options)
        options-in (apply hash-map (drop-while (complement keyword?) sources-and-options))
        hierarchy `(schema/init! (select-keys ~options-in [:db-schema :client-schema]))
        ancestors-fn `(util/make-ancestors-fn ~hierarchy)
        options (mapcat identity
                 (merge {:fact-type-fn :a
                         :ancestors-fn ancestors-fn
                         :activation-group-fn `(util/make-activation-group-fn ~core/default-group)
                         :activation-group-sort-fn `(util/make-activation-group-sort-fn
                                                      ~core/groups ~core/default-group)}
                   (dissoc options-in :db-schema :client-schema)))
        body (into options (conj sources `'precept.impl.rules))]
    `(cm/defsession ~name ~@body)))

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
;; - Use namespaced keywords for :entities so we only match on impl-level attrs
;; - Add test
(defn generate-rules
  [expr idx lhs rhs props]
  (let [ast (eval (add-ns-if-special-form expr))
        var-binding (:join (:gen ast))
        fact-binding (second (first (nth lhs idx)))
        nom (:name props)
        ;; This assumes the binding we're looking for exists in accumulator syntax
        matching-expr (first (filter #(and (has-accumulator? (drop 2 %))
                                        (= var-binding (first %))
                                        (> idx (.indexOf lhs %)))
                               lhs))
        lhs-mod (assoc (vec lhs) idx (parse-as-tuple `[[~'_ ::rulegen/response ~var-binding]]))
        id (gensym "?")
        gen-conds (list [[id ::rulegen/request-params var-binding]]
                        [[id ::rulegen/for-macro :entities]]
                        [[id ::rulegen/response fact-binding]])
        cache (mk-parse-cache)
        rw-lhs (map #(rewrite-expr % cache) (replace-at-index idx gen-conds lhs))
        req-id (precept.util/guid)]
    [{:name (symbol (str nom (-> ast :gen :name-suffix)))
      :lhs (list (parse-with-accumulator matching-expr))
      :rhs `(do
              (precept.util/insert!
                [[~req-id ::rulegen/for-macro :entities]
                 [~req-id ::rulegen/request-params ~var-binding]
                 [~req-id :entities/order ~var-binding]])
              (doseq [eid# ~var-binding]
                (precept.util/insert! [~req-id :entities/eid eid#])))}
     {:name nom
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
    (core/register-rule "rule" lhs rhs)
    `(do ~@(for [{:keys [name lhs rhs]} rule-defs]
            `(cm/defrule ~name ~@passthrough ~@lhs ~'=> (do ~rhs))))))

(defmacro defquery
  "CLJS version of defquery"
  [name & body]
  (let [doc (if (string? (first body)) (first body) nil)
        binding (if doc (second body) (first body))
        definition (if doc (drop 2 body) (rest body))
        rw-lhs      (rewrite-lhs definition)
        passthrough (filter #(not (nil? %)) (list doc binding))]
    (core/register-rule "query" definition nil)
    `(cm/defquery ~name ~@passthrough ~@rw-lhs)))

(defmacro define
  "CLJS version of define"
  [& forms]
  (let [{:keys [body head]} (util/split-head-body forms)
        name (symbol (core/register-rule "define" body head))
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
        sub-rhs (parse-sub-rhs rhs)
        sub-cond `[[[~'?e___sub___impl ::sub/request ~kw]]]
        rule-defs (get-rule-defs (into lhs sub-cond) sub-rhs {:name name :props properties})]
      `(do ~@(for [{:keys [name lhs rhs]} rule-defs]
               `(cm/defrule ~name ~@passthrough ~@lhs ~'=> (do ~rhs))))))
