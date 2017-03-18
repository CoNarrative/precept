(ns todomvc.macros
    (:require [clara.rules :refer [defsession fire-rules insert query defquery]]))
              ;[todomvc.util]))

(defmacro infix
  [infixed]
  (list
    (second infixed)
    (first infixed)
    (last infixed)))

(macroexpand '(infix (1 + 1)))

(defmacro defn-tuple-session
  "Wrapper around Clara's `defsession` macro.
  Preloads query helpers."
  [name & sources-and-options]
  ;(let [options (filter keyword? sources-and-options)])
  `(defsession
    ~name
    ;'todomvc.util
    ~@sources-and-options
    :fact-type-fn ~'(fn [[e a v]] a)
    :ancestors-fn ~'(fn [type] [:all])))

(macroexpand '(defn-tuple-session my-ses *ns*))

(macroexpand
  '(defn-tuple-session my-ses 'todomvc.macros))

(macroexpand '(defsession my-ses 'todomvc.macros
                :fact-type-fn (fn [[e a v]] a)))

(defn-tuple-session my-ses
  'todomvc.macros)

(def wfacts (fire-rules
              (insert my-ses [1 :hello/world true])))

(defquery find-facts []
  [?fact <- :all])

(query wfacts find-facts)



