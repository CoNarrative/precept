(ns precept.dsl)

(defmacro entity
  "Accumulates all facts for entity with eid `e`"
  [e]
  `['(clara.rules.accumulators/all) :from ['~e :all]])

;(defmacro entities
;  [es])
  ;(doseq))

(defmacro <-
  "Binds the result of `form` to `fact-binding`"
  [fact-binding form]
  `(into ['~fact-binding '~'<-] ~form))
