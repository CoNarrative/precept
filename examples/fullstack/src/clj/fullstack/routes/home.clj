(ns fullstack.routes.home
    (:require [hiccup.page :refer [html5 include-css include-js]]
              [compojure.core :refer [defroutes GET]]))

(defn home-page []
  (html5
    [:head
     (include-css "css/bootstrap.min.css")
     (include-css "css/bootstrap-theme.css")
     (include-css "css/app.css")]
    [:div#app]
    (include-js "js/app.js")
    (include-js "https://ajax.googleapis.com/ajax/libs/jquery/1.12.4/jquery.min.js")
    (include-js "js/bootstrap.min.js")))

(defroutes home-routes
  (GET "*" [] (home-page)))
