(ns fullstack.middleware
  (:require [fullstack.env :refer [defaults]]
            [clojure.tools.logging :as log]
            [ring.middleware.format :refer [wrap-restful-format]]
            [fullstack.config :refer [env]]
            [ring.middleware.defaults :refer [site-defaults wrap-defaults]]))

(defn wrap-internal-error [handler]
  (fn [req]
    (try
      (handler req)
      (catch Throwable t
        (log/error t)))))

(defn wrap-formats [handler]
  (let [wrapped (wrap-restful-format
                  handler
                  {:formats [:json-kw :edn :transit-json :transit-msgpack]})]
    (fn [request]
      ;; disable wrap-formats for websockets
      ;; since they're not compatible with this middleware
      ((if (:websocket? request) handler wrapped) request))))

(defn wrap-base [handler]
  (-> ((:middleware defaults) handler)
      (wrap-defaults (assoc-in site-defaults [:security :anti-forgery] false))
      wrap-internal-error))

