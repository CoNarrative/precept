(ns precept.dsl)

(defmacro entity
  "Accumulates all facts for entity with eid `e`"
  [e]
  `['(clara.rules.accumulators/all) :from ['~e :all]])

(defmacro entities
  "Generates rules and facts as indicated by *"
  [es]
  {:name :precept.spec.rulegen/entities
   :gen {:name-suffix "___impl_split-0"
         :join `'~es}})

(defmacro <-
  "Binds the result of `form` to `fact-binding`"
  [fact-binding form]
  `(into ['~fact-binding '~'<-] ~form))
