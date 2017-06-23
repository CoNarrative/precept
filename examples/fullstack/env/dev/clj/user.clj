(ns user
  (:require [mount.core :as mount]
            [fullstack.figwheel :refer [start-fw stop-fw cljs]]
            [fullstack.core]))

(defn start []
  (mount/start-without #'fullstack.core/http-server
                       #'fullstack.core/repl-server))

(defn stop []
  (mount/stop-except #'fullstack.core/http-server
                     #'fullstack.core/repl-server))

(defn restart []
  (stop)
  (start))


