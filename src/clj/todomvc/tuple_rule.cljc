(ns todomvc.tuple-rule
    (:require [clara.rules.compiler :as com]
              [clara.rules.dsl :as dsl]))


;(defn- parse-expression
;  "Convert each expression into a condition structure."
;  [expression expr-meta]
;  (cond
;
;    (contains? dsl/ops (first expression))
;    (into [(keyword (name (first expression)))]             ; Ensure expression operator is a keyword.
;      (for [nested-expr (rest expression)]
;        (parse-expression nested-expr expr-meta)))
;
;    (contains? #{'test :test} (first expression))
;    {:constraints (vec (rest expression))}
;
;    :default
;    (dsl/parse-condition-or-accum expression expr-meta)))
;
;(defn parse-tuple-rule*
;  "Creates a rule from the DSL syntax using the given environment map.  *ns*
;   should be bound to the namespace the rule is meant to be defined in."
;  ([lhs rhs properties env]
;   (parse-tuple-rule* lhs rhs properties env {}))
;  ([lhs rhs properties env rule-meta]
;   (let [conditions   (into [] (for [expr lhs]
;                                 (parse-expression expr rule-meta)))
;
;         rule         {:ns-name (list 'quote (ns-name *ns*))
;                       :lhs     (list 'quote
;                                  (mapv #(dsl/resolve-vars % (dsl/destructure-syms %))
;                                    conditions))
;                       :rhs     (list 'quote
;                                  (vary-meta rhs
;                                    assoc :file *file*))}
;
;         symbols      (set (filter symbol? (com/flatten-expression (concat lhs rhs))))
;         matching-env (into {} (for [sym (keys env)
;                                     :when (symbols sym)]
;                                 [(keyword (name sym)) sym]))]
;
;     (cond-> rule
;
;       ;; Add properties, if given.
;       (not (empty? properties)) (assoc :props properties)
;
;       ;; Add the environment, if given.
;       (not (empty? env)) (assoc :env matching-env)))))
(defn is-binding? [x]
  ;(println "Is a binding?" x)
  ;TODO. Long cannot be cast to Named
  (= (first (name x)) \?))

(defn rewrite-lhs [forms]
  (println "Forms" forms)
  (mapv (fn main-one [form]
         ;(println "Form" form)
         ;(println "Type" (type form))
         (println "is op" (dsl/ops (first form)))
         (if (dsl/ops (first form))
           form
           (let [bindings (into {}
                            (filter
                              (fn [[k v]]
                                (and (not= \_ v)
                                     (identity v)
                                     (is-binding? v)))
                              ;(juxt (comp identity second)
                              ;      (partial (not= \_)) second
                              ;      (comp is-binding? second)))
                              {:e (first form)
                               :a (second form)
                               :v (last form)}))
                 attribute (if (keyword? (second form)) (second form) :all)]
             (prn "Bindigs are" bindings)
             ;(println "attribute is" attribute)
            (vector
              attribute (vector ['e 'a 'v])
              (map (fn last-one [[k v]]
                       (println "k v bindings" k v)
                       (list '= v (symbol (name k)))) bindings)))))

    forms))
#?(:clj
   (defmacro def-tuple-rule
     "Defines a rule and stores it in the given var. For instance, a simple rule would look like this:

     (defrule hvac-approval
       \"HVAC repairs need the appropriate paperwork, so insert
         a validation error if approval is not present.\"
       [WorkOrder (= type :hvac)]
       [:not [ApprovalForm (= formname \"27B-6\")]]
       =>
       (insert! (->ValidationError
                 :approval
                 \"HVAC repairs must include a 27B-6 form.\")))

 See the [rule authoring documentation](http://www.clara-rules.org/docs/rules/) for details."
     [name & body]
     (if (com/compiling-cljs?)
       `(clara.macros/defrule ~name ~@body)
       (let [doc        (if (string? (first body)) (first body) nil)
             body       (if doc (rest body) body)
             properties (if (map? (first body)) (first body) nil)
             definition (if properties (rest body) body)
             {:keys [lhs rhs]} (dsl/split-lhs-rhs definition)
             lhs-new    (rewrite-lhs (first lhs))]
         (println "LHS" lhs-new)
         (println "RHS" rhs)
         (when-not rhs
           (throw (ex-info (str "Invalid rule " name ". No RHS (missing =>?).")
                    {})))
         `(def ~(vary-meta name assoc :rule true :doc doc)
            (cond-> ~(dsl/parse-rule* lhs-new rhs properties {} (meta &form))
              ~name (assoc :name ~(str (clojure.core/name (ns-name *ns*)) "/" (clojure.core/name name)))
              ~doc (assoc :doc ~doc)))))))

;LHS ([[?e :todo/title _]] [:exists [:todo/done]])

;LHS ([:todo/title [[e a v]] (= ?e e)] [:exists [:todo/done]])
(rewrite-lhs (first
               '([
                  [?e :todo/title _]
                  [:exists [:todo/done]]])))

(macroexpand
  '(def-tuple-rule my-tuple-rule
     "Docstring!!"
     [[?e :todo/title _]]
     [:exists [:todo/done]]
     =>
     (println "Hello!")))

(macroexpand
  '(def-tuple-rule my-regular-rule
     "Docstring!!"
     [:todo/title [[e a v]] (= ?e e)]
     [:exists [:todo/done]]
     =>
     (println "Hello!")))


(macroexpand
  '(defrule my-rule
     [:todo/title [[e a v]] (= e? e)]
     =>
     (println "Hello!")))
