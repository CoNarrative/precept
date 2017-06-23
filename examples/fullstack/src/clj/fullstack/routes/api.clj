(ns fullstack.routes.api
    (:require [compojure.core :refer [defroutes GET POST PUT DELETE]]
              [fullstack.db.core :refer [db]]
              [precept.util :as util]))


(defn get-cart []
  {:status 200 :body (vals (:cart @db))})

(defn get-products []
  {:status 200 :body (:products @db)})

(defn add-to-cart! [{:keys [db/id cart-item/product-id cart-item/quantity]}]
  (do (swap! db assoc-in [:cart id]
          {:db/id id
           :cart-item/quantity quantity
           :cart-item/product-id product-id})
      {:status 200 :body (get-in @db [:cart id])}))

(defn remove-from-cart! [id]
  (do (swap! db util/dissoc-in [:cart id])
      {:status 200 :body id}))

(defn reset-cart! []
  (do (swap! db assoc :cart {})
      {:status 200 :body (vals (:cart @db))}))

(defn update-quantity! [id quantity]
  (do (swap! db assoc-in [:cart id :cart-item/quantity] quantity)
      {:status 200 :body {:db/id id :cart-item/quantity quantity}}))

(defroutes api-routes
  (GET "/products" [] (get-products))
  (GET "/cart" [] (get-cart))
  (POST "/cart" [m] (add-to-cart! m))
  (DELETE "/cart/:id" [id] (remove-from-cart! id))
  (PUT "/cart/:id/quantity" [id quantity] (update-quantity! id quantity))
  (DELETE "/cart" [] (reset-cart!)))
