(ns todomvc.tuple-rule)
;    (:require
;            #?(:clj [clara.rules.compiler :as com])
;            #?(:clj [clara.rules.dsl :as dsl]))
;    #?(:cljs (:require-macros todomvc.tuple-rule)))
;
;;; from clara.rules.dsl
;(def ops #{'and 'or 'not 'exists :and :or :not :exists})
;
;;(defn- parse-expression
;;  "Convert each expression into a condition structure."
;;  [expression expr-meta]
;;  (cond
;;
;;    (contains? dsl/ops (first expression))
;;    (into [(keyword (name (first expression)))]             ; Ensure expression operator is a keyword.
;;      (for [nested-expr (rest expression)]
;;        (parse-expression nested-expr expr-meta)))
;;
;;    (contains? #{'test :test} (first expression))
;;    {:constraints (vec (rest expression))}
;;
;;    :default
;;    (dsl/parse-condition-or-accum expression expr-meta)))
;;
;;(defn parse-tuple-rule*
;;  "Creates a rule from the DSL syntax using the given environment map.  *ns*
;;   should be bound to the namespace the rule is meant to be defined in."
;;  ([lhs rhs properties env]
;;   (parse-tuple-rule* lhs rhs properties env {}))
;;  ([lhs rhs properties env rule-meta]
;;   (let [conditions   (into [] (for [expr lhs]
;;                                 (parse-expression expr rule-meta)))
;;
;;         rule         {:ns-name (list 'quote (ns-name *ns*))
;;                       :lhs     (list 'quote
;;                                  (mapv #(dsl/resolve-vars % (dsl/destructure-syms %))
;;                                    conditions))
;;                       :rhs     (list 'quote
;;                                  (vary-meta rhs
;;                                    assoc :file *file*))}
;;
;;         symbols      (set (filter symbol? (com/flatten-expression (concat lhs rhs))))
;;         matching-env (into {} (for [sym (keys env)
;;                                     :when (symbols sym)]
;;                                 [(keyword (name sym)) sym]))]
;;
;;     (cond-> rule
;;
;;       ;; Add properties, if given.
;;       (not (empty? properties)) (assoc :props properties)
;;
;;       ;; Add the environment, if given.
;;       (not (empty? env)) (assoc :env matching-env)))))
;(defn binding? [x]
;  (println "Is a binding?" x)
;  (and
;    (symbol? x)
;    (= (first (name x)) \?)))
;
;(defn sexpr? [x]
;  (println "Is a sexpr?" x)
;  (and
;    (list? x)))
;;(fn? (first x))))
;
;(defn value-expr? [x]
;  (println "Is a value-expr?" x)
;  (and
;    (not= \_ x)
;    (not (binding? x))
;    (not (sexpr? x))))
;
;
;(defn variable-bindings [tuple]
;  (into {}
;    (filter (fn [[k v]] (binding? v))
;      {:e (first tuple)
;       :a (second tuple)
;       :v (last tuple)})))
;
;(defn sexprs [tuple]
;  (into {}
;    (filter (fn [[k v]]
;              []
;              (sexpr? v))
;      {:a (second tuple)
;       :v (last tuple)})))
;
;(defn positional-value [tuple]
;  (into {}
;    (filter (fn [[k v]]
;              (value-expr? v))
;      {:v (last tuple)})))
;
;(defn parse-as-tuple [expr]
;  (let [tuple                          (first expr)
;        bindings                       (variable-bindings tuple)
;        positional-sexprs              (println (sexprs tuple))
;        bindings-and-constraint-values (merge bindings
;                                         (sexprs tuple)
;                                         (positional-value tuple))
;
;        value-expressions              (positional-value tuple)
;        attribute                      (if (keyword? (second tuple)) (second tuple) :all)]
;    (println "Tuple: " tuple)
;    (println "Variable bindings for form:" bindings)
;    (println "Value expressions for form" value-expressions)
;    (println "With s-exprs merged:" bindings-and-constraint-values)
;    (reduce
;      (fn last-one [rule-expr [eav v]]
;        (println "K V" eav v)
;        (conj rule-expr
;          (if (list? v)                                     ; s-expr?
;            v
;            (list '= v (symbol (name eav))))))
;      (vector attribute (vector ['e 'a 'v]))
;      bindings-and-constraint-values)))
;
;(defn parse-with-fact-expression [expr]
;  (let [fact-expression (take 2 expr)
;        expression      (drop 2 expr)]
;    (conj (lazy-seq (parse-as-tuple expression))
;      (second fact-expression)
;      (first fact-expression))))
;
;
;(defn rewrite-lhs [exprs]
;  (mapv (fn main-one [expr]
;          (let [leftmost        (first expr)
;                op              (keyword? (dsl/ops leftmost))
;                fact-expression (and (not (keyword? leftmost))
;                                  (not (vector? leftmost))
;                                  (binding? leftmost))]
;            (cond
;              op expr
;              fact-expression (parse-with-fact-expression expr)
;              :else (parse-as-tuple expr))))
;    exprs))
;
;#?(:clj
;   (defmacro def-tuple-rule
;     "Defines a rule and stores it in the given var. For instance, a simple rule would look like this:
;
;     (defrule hvac-approval
;       \"HVAC repairs need the appropriate paperwork, so insert
;         a validation error if approval is not present.\"
;       [WorkOrder (= type :hvac)]
;       [:not [ApprovalForm (= formname \"27B-6\")]]
;       =>
;       (insert! (->ValidationError
;                 :approval
;                 \"HVAC repairs must include a 27B-6 form.\")))
;
; See the [rule authoring documentation](http://www.clara-rules.org/docs/rules/) for details."
;     [name & body]
;     (if (com/compiling-cljs?)
;       `(clara.macros/defrule ~name ~@body)
;       (let [doc             (if (string? (first body)) (first body) nil)
;             body            (if doc (rest body) body)
;             properties      (if (map? (first body)) (first body) nil)
;             definition      (if properties (rest body) body)
;             {:keys [lhs rhs]} (dsl/split-lhs-rhs definition)
;             lhs-detuplified (rewrite-lhs lhs)]
;         ;(println "LHS in" lhs)
;         (println "LHS out" lhs-detuplified)
;         (when-not rhs
;           (throw (ex-info (str "Invalid rule " name ". No RHS (missing =>?).")
;                    {})))
;         `(def ~(vary-meta name assoc :rule true :doc doc)
;            (cond-> ~(dsl/parse-rule* lhs-detuplified rhs properties {} (meta &form))
;              ~name (assoc :name ~(str (clojure.core/name (ns-name *ns*)) "/" (clojure.core/name name)))
;              ~doc (assoc :doc ~doc)))))))
;
;;LHS ([[?e :todo/title _]] [:exists [:todo/done]])
;
;;LHS ([:todo/title [[e a v]] (= ?e e)] [:exists [:todo/done]])
;(rewrite-lhs '([[?e :todo/title _]] [:exists [:todo/done]]))
;
;;TODO. Test these also. SHould work and be equivalent since b0c3f1c082dde6019675cc24a5a0120ce9c544ac
;(macroexpand
;  '(def-tuple-rule my-tuple-rule
;     "Docstring!!"
;     [?todo <- [?e :todo/title _]]
;     [:exists [:todo/done]]
;     =>
;     (println "Hello!")))
;
;(macroexpand
;  '(def-tuple-rule my-tuple-rule
;     "Docstring!!"
;     [[?e :todo/title ?v]]
;     [[?e2 :todo/title ?v]]
;     [:exists [:todo/done]]
;     =>
;     (println "Hello!")))
;
;;(macroexpand
;;  '(defrule my-rule
;;     "Docstring!!"
;;     [:todo/title [[e a v]] (= ?e e) (= v ?v)]
;;     [:todo/title [[e a v]] (not= e ?e) (= ?v2 ?v)]
;;     [:exists [:todo/done]]
;;     =>
;;     (println "Hello!")))
;
;;(macroexpand
;;  '(defrule my-regular-rule
;;     "Docstring!!"
;;     [?todo <- :todo/title [[e a v]] (= ?e e)]
;;     [:exists [:todo/done]]
;;     =>
;;     (println "Hello!")))
;
;;TODO. Test these. Should be equivalent as of 23814274a376f12535c455a55ed9d5e85c81f5c9
;(macroexpand
;  '(def-tuple-rule my-tuple-rule
;     "Docstring!!"
;     [[?e :todo/title _]]
;     [:exists [:todo/done]]
;     =>
;     (println "Hello!")))
;
;;(macroexpand
;;  '(defrule my-regular-rule
;;     "Docstring!!"
;;     [:todo/title [[e a v]] (= ?e e)]
;;     [:exists [:todo/done]]
;;     =>
;;     (println "Hello!")))
;;
;;
;;(macroexpand
;;  '(defrule my-rule
;;     [:todo/title [[e a v]] (= e? e)]
;;     =>
;;     (println "Hello!")))
;
