(ns fullstack.env
  (:require [clojure.tools.logging :as log]
            [fullstack.dev-middleware :refer [wrap-dev]]))

(def defaults
  {:init
   (fn []
     (log/info "\n-=[precept-fullstack started successfully using the development profile]=-"))
   :stop
   (fn []
     (log/info "\n-=[precept-fullstack has shut down successfully]=-"))
   :middleware wrap-dev})
