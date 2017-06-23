(ns fullstack.handler
    (:require [compojure.core :refer [routes wrap-routes]]
              [fullstack.routes.home :refer [home-routes]]
              [fullstack.routes.ws :refer [ws-routes]]
              [compojure.route :as route]
              [fullstack.env :refer [defaults]]
              [mount.core :as mount]
              [fullstack.routes.api :refer [api-routes]]
              [fullstack.middleware :as middleware]))

(mount/defstate init-app
  :start ((or (:init defaults) identity))
  :stop  ((or (:stop defaults) identity)))

(def app-routes
  (routes
    #'ws-routes
    (-> #'api-routes (wrap-routes middleware/wrap-formats))
    (-> #'home-routes (wrap-routes middleware/wrap-formats))
    (route/not-found {:status 404})))

(defn app [] (middleware/wrap-base #'app-routes))
