(ns fullstack.db.core
  (:require [fullstack.db.data :as data]))


(def products (into [] (repeatedly 100 data/mk-product)))

(def cart
  (reduce
    (fn [acc eid]
      (let [cart-id (java.util.UUID/randomUUID)]
       (assoc acc cart-id {:db/id cart-id
                           :cart-item/product-id eid
                           :cart-item/quantity 1})))
    {}
    (take 2 (set (map :db/id products)))))

(def promotions
  (let [discount1 (java.util.UUID/randomUUID)
        discount2 (java.util.UUID/randomUUID)
        promotion1 (java.util.UUID/randomUUID)
        promotion2 (java.util.UUID/randomUUID)]
    [{:db/id discount1
      :discount/percent-subtotal 20}
     {:db/id discount2
      :discount/percent-total 15
      :discount/message "15% off cart total activated!"}
     {:db/id promotion1
      :promotion/discount-id discount1
      :promotion/same-item 3}
     {:db/id promotion2
      :promotion/discount-id discount2
      :promotion/total-items 5}]))

(def db (atom {:products products
               :cart cart
               :promotions promotions}))
