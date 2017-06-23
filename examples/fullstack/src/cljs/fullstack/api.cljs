(ns fullstack.api
  (:require [precept.core :refer [then]]
            [ajax.core :refer [GET POST PUT DELETE]]))


(defn get-products []
  (GET "/products"
    {:handler #(then (conj % [:transient :api/success :fetch-products]))
     :error-handler #(then [(random-uuid) :error %])}))

(defn get-cart []
  (GET "/cart"
    {:handler #(then (mapcat precept.util/map->tuples %))
     :error-handler #(.log js/console %)}))

(defn add-to-cart [m]
  (POST "/cart"
    {:params {:m m}
     :handler #(then (precept.util/map->tuples %))
     :error-handler #(.error js/console %)}))

(defn update-quantity [{:keys [db/id cart-item/quantity]}]
  (PUT (str "/cart/" id "/quantity")
    {:params {:id id :quantity quantity}
     :handler #(then (precept.util/map->tuples %))
     :error-handler #(.error js/console %)}))

(defn delete-cart-item [id]
  (DELETE (str "/cart/" id)
    {:params {:id id}
     :handler #(then [:transient :remove-entity %])
     :error-handler #(.error js/console %)}))

(defn delete-cart []
  (DELETE "/cart"
    {:handler (fn [x] (then [:transient :clear-cart true]))
     :error-handler #(.error js/console %)}))
