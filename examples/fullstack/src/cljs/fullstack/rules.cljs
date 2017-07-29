(ns fullstack.rules
  (:require-macros [precept.dsl :refer [<- entities entity]])
  (:require [precept.rules :refer [rule session defsub define]]
            [precept.util :refer [retract! insert! insert-unconditional!] :as util]
            [precept.accumulators :as acc]
            [fullstack.schema :refer [db-schema client-schema]]
            [fullstack.api :as api]))

(defn with-precision-2 [n]
   (.parseFloat js/Number (.toFixed n 2)))

(defn total-with-percent-off [total percent]
  (with-precision-2 (* total (/ (- 100 percent) 100))))

;; Commands
(rule initial-state-on-start
  {:group :action}
  [[_ :start]]
  =>
  (insert-unconditional! [:app :product-hovered nil])
  (api/get-cart)
  (api/get-products))

(rule clear-cart-on-command
  {:group :action}
  [[_ :clear-cart]]
  [?item <- [_ :cart-item/product-id]]
  =>
  (retract! ?item))

(rule add-item-to-cart-when-not-in-cart-on-command
  {:group :action}
  [[_ :add-to-cart ?product-id]]
  [:not [_ :cart-item/product-id ?product-id]]
  =>
 (api/add-to-cart {:db/id (random-uuid)
                   :cart-item/product-id ?product-id
                   :cart-item/quantity 1}))

(rule update-quantity-if-existing-product-on-add-to-cart-command
  {:group :action}
  [[_ :add-to-cart ?product-id]]
  [[?existing :cart-item/product-id ?product-id]]
  [[?existing :cart-item/quantity ?quantity]]
  =>
  (api/update-quantity {:db/id ?existing
                        :cart-item/quantity (inc ?quantity)}))

(rule update-quantity-on-command
  {:group :action}
  [[_ :update-quantity/quantity ?quantity]]
  [[_ :update-quantity/id ?id]]
  =>
  (api/update-quantity {:db/id ?id
                        :cart-item/quantity ?quantity}))

(rule remove-item-from-cart-on-command
  {:group :action}
  [[_ :remove-from-cart ?v]]
  =>
  (api/delete-cart-item ?v))

;; Price calculations

; item subtotal before any discounts
(define [?e :cart-item/undiscounted-subtotal (with-precision-2 (* ?quantity ?price))]
  :- [[?e :cart-item/product-id ?product-id]]
     [[?product-id :product/price ?price]]
     [[?e :cart-item/quantity ?quantity]])

; item subtotal when no per-item discount
(define [?e :cart-item/subtotal ?undiscounted-subtotal]
  :- [:not [?e :cart-item/dollars-off]]
     [[?e :cart-item/undiscounted-subtotal ?undiscounted-subtotal]])

; item subtotal when per-item discount
(define [?e :cart-item/subtotal (with-precision-2 (* ?quantity (- ?price ?dollars-off)))]
  :- [[?e :cart-item/dollars-off ?dollars-off]]
     [[?e :cart-item/product-id ?product-id]]
     [[?product-id :product/price ?price]]
     [[?e :cart-item/quantity ?quantity]])

; item price when per-item discount
(rule adjust-item-price-when-qualifies
  [[?cart-item :qualifies-for-discount ?discount-id]]
  [[?discount-id :discount/percent-subtotal ?percent]]
  [[?cart-item :cart-item/product-id ?product-id]]
  [[?product-id :product/price ?price]]
  =>
  (let [adjusted-price (* ?price (/ (- 100 ?percent) 100))]
    (insert! {:db/id ?cart-item
              :cart-item/dollars-off (with-precision-2 (- ?price adjusted-price))
              :cart-item/adjusted-price (with-precision-2 adjusted-price)})))

; cart total before any discounts
(define [:app :cart/undiscounted-total ?total]
  :- [?total <- (acc/sum :v) :from [_ :cart-item/undiscounted-subtotal]])

(define [:app :summed-subtotals ?sum] :- [?sum <- (acc/sum :v) :from [_ :cart-item/subtotal]])

; cart total when no percent total discount
(define [:app :cart/total ?total] :- [:not [_ :active-discount]]
  [[_ :summed-subtotals ?total]])

; cart total when percent total discount
(define [:app :cart/total (total-with-percent-off ?total ?percent-discount)]
  :- [[_ :active-discount ?id]]
     [[?id :discount/percent-total ?percent-discount]]
     [[_ :summed-subtotals ?total]])


;; Filtering

; product visible when no filter
(define [:app :visible-product/id ?e] :- [:not [_ :product-filter/range]]
                                         [[?e :product/name]])

; product visible when filter active and price in range
(define [:app :visible-product/id ?e]
  :- [[_ :product-filter/range ?range]]
     [[?e :product/price ?price]]
     [:test (<= (first ?range) ?price (second ?range))])


;; Sorting

(define [?e :sort-comparator <] :- [[?e :sort-by :asc]])

(define [?e :sort-comparator >] :- [[?e :sort-by :desc]])

(define [:app :sort-fn identity] :- [:not [_ :sort-by]]
                                    [:not [_ :order-by]])

(define [:app :sort-fn #(sort-by ?order-by ?sort-by %)] :- [[_ :sort-comparator ?sort-by]]
                                                           [[_ :order-by ?order-by]])

(rule sort-products-list
  [[_ :sort-fn ?sort-fn]]
  [?ids <- (acc/all :v) :from [_ :visible-product/id]]
  [(<- ?products (entities ?ids))]
  =>
  (let [transformed (->> (util/Tuples->maps ?products) (?sort-fn))]
    (insert! [:app :products/list transformed])))


;; Promotions

; item qualifies for # of same item discount
(define [?e :qualifies-for-discount ?discount-id]
  :- [[?promo :promotion/same-item ?n]]
     [[?promo :promotion/discount-id ?discount-id]]
     [[?e :cart-item/quantity ?quantity]]
     [:test (>= ?quantity ?n)])

; cart qualifies total # of items promotion
(rule total-items-promotion
  [[?promo :promotion/total-items ?num-items]]
  [?cart-items <- (acc/sum :v) :from [_ :cart-item/quantity]]
  [:test (>= ?cart-items ?num-items)]
  [[?promo :promotion/discount-id ?discount-id]]
  [[?discount-id :discount/message ?message]]
  =>
  (insert! [{:db/id :header
             :alert/message ?message
             :alert/class "alert-success"}
            {:db/id (random-uuid)
             :active-discount ?discount-id}]))

;; Subscriptions

(defsub :products
  [[_ :products/list ?products]]
  [[_ :product-hovered ?e]]
  =>
  {:products ?products
   :hovered ?e})

(define [?cart-id :cart-item/product ?product] :- [[?cart-id :cart-item/product-id ?product-id]]
                                                  [(<- ?product (entity ?product-id))])

(defsub :cart
  [[_ :cart/total ?total]]
  [[_ :cart/undiscounted-total ?undiscounted-total]]
  [?cart-ids <- (acc/all :e) :from [_ :cart-item/product-id]]
  [(<- ?cart (entities ?cart-ids))]
  =>
  (let [total (with-precision-2 ?total)
        undiscounted-total (with-precision-2 ?undiscounted-total)]
    {:cart ?cart
     :total total
     :undiscounted-total undiscounted-total
     :show-undiscounted? (not= total undiscounted-total)
     :total-savings (with-precision-2 (- undiscounted-total total))}))

(defsub :sort-menu
  [[_ :sort-menu/selected ?v]]
  =>
  {:selected ?v})

(defsub :filter-menu
  [[_ :filter-menu/selected ?v]]
  =>
  {:selected ?v})

(defsub :header
  [(<- ?props (entity :header))]
  =>
  {:props ?props})

;; Error handler

(rule catch-error-facts
  [?err <- [_ :precept.spec.error/type]]
  =>
  (.error js/console "Error fact: " ?err))

;; Define session

(session app-session 'fullstack.rules
  :db-schema db-schema
  :client-schema client-schema
  :reload true)
