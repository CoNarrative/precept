(ns precept.dsl)

(defmacro entity
  "Accumulates all facts for entity with eid `e`"
  [e]
  `['(clara.rules.accumulators/all) :from ['~e :all]])

(defmacro entities
  "Generates rules and facts as indicated by *"
  [es]
  {:gen {:name-suffix "___impl_split-0"
         :join `'~es}})

(defmacro <-
  "Binds the result of `form` to `fact-binding`"
  [fact-binding form]
  `(into ['~fact-binding '~'<-] ~form))

(defmacro mk-rules [rules]
  (let [rs [{:name 'foo :body "fooob"}
            {:name 'bar :body "baz"}]
        _ (println "rs" rs)]
    `(do
       ~@(for [{:keys [name body]} rs]
           `(def ~name
              (cond-> ~(first rs)))))))

(mk-rules [{:name foo :body "fooob"}
           {:name bar :body "baz"}])

bar


