(ns user
    (:require [clojure.tools.namespace.repl :refer [refresh]]
              [precept.figwheel :refer [start-fw stop-fw cljs]]))

(defn reload []
    (refresh))
