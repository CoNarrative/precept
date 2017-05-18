(ns precept.dsl)

(defmacro entity
 [e]
 `['(clara.rules.accumulators/all) :from ['~e :all]])

(defmacro <-
 [fact-binding form]
 `(into ['~fact-binding '~'<-] ~form))
