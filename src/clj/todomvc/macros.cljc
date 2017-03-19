(ns todomvc.macros
    #?(:cljs (:require-macros [clara.rules :refer [defsession]]))
    #?(:clj
       (:require [clara.rules :refer [defsession defrule]])))

(defmacro def-tuple-session
  "Wrapper around Clara's `defsession` macro.
  Preloads query helpers."
  [name & sources-and-options]
  `(defsession
     ~name
     'todomvc.util
     ~@sources-and-options
     :fact-type-fn ~'(fn [[e a v]] a)
     :ancestors-fn ~'(fn [type] [:all])))

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
(defmacro tuple-rule
  [name & body])

(macroexpand
  '(defrule foo
    ; when toggle complete action exists
    [:exists [:ui/toggle-complete]]
    ; and there's a todo that isn't marked "done"
    [:todo/title [[e a v]] (= ?e e)]
    [:not [:todo/done [[e a v]] (= ?e e)]]
    =>
    (println "Marked done via toggle complete:" ?e)
    (insert-unconditional! [?e :todo/done :done])))


(macroexpand
  '(defrule bar
     ; if arity one match on attr
     [:exists [:ui/toggle-complete]]
     ; if arity 3 assume e a v
     [[?e :todo/title _]]
     [:not [[?e :todo/done ?v] (> ?v 3)]]
     =>
     (println "Marked done via toggle complete:" ?e)
     (insert-unconditional! [?e :todo/done :done])))


