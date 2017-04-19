(ns user
    (:require [clojure.tools.namespace.repl :refer [refresh]]
              [libx.figwheel :refer [start-fw stop-fw cljs]]))

(defn reload []
    (refresh))
