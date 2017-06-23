(ns fullstack.views
  (:require [precept.core :refer [subscribe then]]
            [reagent.core :as reagent]
            [precept.state :as state]))


(defn format-price [x] (str "$" (goog.string/format "%.2f" x)))

(defn header []
  (let [{:keys [props]} @(subscribe [:header])
        alert (first props)]
    [:nav {:class "navbar navbar-fixed-top navbar-default"}
     [:div {:class "container-fluid"}
      [:div {:class "navbar-header"}
       [:button {:type "button" :class "navbar-toggle collapsed" :data-toggle "collapse" :data-target "#bs-example-navbar-collapse-1" :aria-expanded "false"}
        [:span {:class "sr-only"} "Toggle navigation"]
        [:span {:class "icon-bar"}]
        [:span {:class "icon-bar"}]
        [:span {:class "icon-bar"}]]
       [:a {:class "navbar-brand" :href "#"} "Precept"]]
      [:div {:class "collapse navbar-collapse" :id "bs-example-navbar-collapse-1"}
       [:ul {:class "nav navbar-nav"}
        [:li {:class "active"}
         [:a {:href "#"} "Shop "
          [:span {:class "sr-only"} "(current)"]]]]
       [:ul {:class "nav navbar-nav navbar-right"}
        (when alert
          [:li {:class (str "alert " (:alert/class alert))}
           (:alert/message alert)])
        [:li
         [:a {:href "#" :data-toggle "modal" :data-target "#myModal"
              :class "btn btn-secondary btn-lg"}
            [:span {:class "glyphicon glyphicon-shopping-cart"}]]]]]]]))

(defn cart-item [id product quantity subtotal adjusted-price dollars-off]
  [:tr
   [:td {:data-th "Product"}
    [:div {:class "row"}
     [:div {:class "col-sm-4 hidden-xs"}
      [:img {:src "http://placehold.it/100x100" :alt "..." :class "img-responsive"}]]
     [:div {:class "col-sm-8"}
      [:h4 {:class "nomargin"}
       (:product/name product)]
      [:p "This is a sample product description."]]]]
   [:td {:data-th "Price"}
    (if-not adjusted-price
      (format-price (:product/price product))
      [:div
        [:del (format-price (:product/price product))]
        [:br]
        (format-price adjusted-price)])]
   [:td {:data-th "Quantity"}
    [:input {:type "number" :min 0 :class "form-control text-center" :value quantity
             :on-change #(then {:db/id :transient
                                :update-quantity/quantity (.parseInt js/Number (-> % .-target .-value))
                                :update-quantity/id id})}]]
   [:td {:data-th "Subtotal" :class "text-center"}
    (format-price (or subtotal 0))]
   [:td {:class "actions" :data-th "Actions"}
    [:button {:class "btn btn-danger btn-sm"
              :on-click #(then [:transient :remove-from-cart id])}
     [:i {:class "fa fa-trash-o"}]
     "Remove"]]])

(defn cart []
  (let [{:keys [cart total total-savings undiscounted-total show-undiscounted?]}
        @(subscribe [:cart])]
    [:div {:class "modal fade bs-example-modal-lg" :id "myModal"}
     [:div {:class "modal-dialog"}
      [:div {:class "modal-content"}
       [:div {:class "modal-header"}
        [:button {:type "button" :class "close" :data-dismiss "modal" :aria-hidden "true"} "×"]
        [:h3 {:class "modal-title"} "Precept Cart"]]
       [:div {:class "modal-body"}
        [:table {:class "table table-condensed"}
         [:thead
          [:tr
           [:th {:style {:width "50%"}} "Product"]
           [:th {:style {:width "10%"}} "Price"]
           [:th {:style {:width "8%"}} "Quantity"]
           [:th {:style {:width "22%"} :class "text-center"} "Subtotal"]
           [:th {:style {:width "10%"}}]]]
         [:tbody
          (for [{:keys [db/id cart-item/quantity cart-item/product cart-item/subtotal
                        cart-item/adjusted-price cart-item/dollars-off]} cart]
             ^{:key id} [cart-item id (first product) quantity subtotal
                         adjusted-price dollars-off])]
         [:tfoot
          [:tr {:class "visible-xs"}
           [:td {:class "text-center"}
            (if-not show-undiscounted?
              [:strong (str "Total " (format-price (or total 0)))]
              [:div
                [:del (format-price undiscounted-total)]
                [:br]
                [:strong (str "Total " (format-price total))]])
            (when (> total-savings 0)
              [:strong (str "You saved " (format-price total-savings) "!")])]]
          [:tr
           [:td {:colSpan "3" :class "hidden-xs"}]
           [:td {:class "hidden-xs text-center"}
            (if-not show-undiscounted?
              [:strong (str "Total " (format-price (or total 0)))]
              [:div
               [:strong "Total "]
               [:del (format-price undiscounted-total)]
               [:br]
               [:strong (format-price total)]])]]
          (when (> total-savings 0)
           [:tr
            [:td {:class "hidden-xs text-center"}
             [:strong (str "You saved " (format-price total-savings) "!")]]])]]]
       [:div {:class "modal-footer"}
        [:button {:type "button" :class "btn btn-default " :data-dismiss "modal"} "Close"]]]]]))

(defn intro []
      [:div {:class "jumbotron"}
       [:h1 "Welcome to Precept Cart!"]
       [:p "Filter products! Sort products! Filter and sort products!
            Add items to the cart to activate discounts and promotions!
            Remove them and deactivate discounts!"]
       [:p
        [:a {:class "btn btn-primary btn-lg" :href "https://conarrative.github.io/precept/" :role "button"}
         "Precept Docs"]]])

(def sort-option-data
  {:name [{:label "A to Z"
           :facts {:db/id :product-sort
                   :sort-menu/selected "A to Z"
                   :sort-by :asc
                   :order-by :product/name}}
          {:label "Z to A"
           :facts {:db/id :product-sort
                   :sort-menu/selected "Z to A"
                   :sort-by :desc
                   :order-by :product/name}}]
   :price [{:label "Low to High"
            :facts {:db/id :product-sort
                    :sort-menu/selected "Low to High"
                    :sort-by :asc
                    :order-by :product/price}}
           {:label "High to Low"
            :facts {:db/id :product-sort
                    :sort-menu/selected "High to Low"
                    :sort-by :desc
                    :order-by :product/price}}]})

(def filter-option-data
  {:range [{:label "$0 to $25"
            :facts {:db/id :product-filter
                    :filter-menu/selected "$0 to $25"
                    :product-filter/range [0 10]}}
           {:label "$25 to $50"
            :facts {:db/id :product-filter
                    :filter-menu/selected "$25 to $50"
                    :product-filter/range [25 50]}}
           {:label "$50 to $75"
            :facts {:db/id :product-filter
                    :filter-menu/selected "$50 to $75"
                    :product-filter/range [50 75]}}
           {:label "$75 to $100"
            :facts {:db/id :product-filter
                    :filter-menu/selected "$75 to $100"
                    :product-filter/range [75 100]}}]})

(defn menu-option [{:keys [label facts]}]
  [:li
   [:a {:on-click #(then facts)}
     label]])

(defn filter-menu []
  (let [{:keys [selected]} @(subscribe [:filter-menu])]
    [:div {:class "col-sm-1"}
     [:label "Filter:"]
     [:div {:class "dropdown"}
      [:button {:class "btn btn-default dropdown-toggle" :type "button" :id "dropdownMenu1" :data-toggle "dropdown" :aria-haspopup "true" :aria-expanded "true"}
       (or selected "Filter")
       [:span {:class "caret"}]]
      [:ul {:class "dropdown-menu" :aria-labelledby "dropdownMenu1"}
       [:li {:class "dropdown-header"} "Price"]
       (for [props (:range filter-option-data)]
         ^{:key (:label props)} [menu-option props])
       [:li {:role "separator" :class "divider"}]
       [menu-option {:label "Clear"
                     :facts {:db/id :transient :remove-entity :product-filter}}]]]]))

(defn sort-menu []
  (let [{:keys [selected]} @(subscribe [:sort-menu])]
    [:div {:class "col-sm-1"}
     [:label "Sort:"]
     [:div {:class "dropdown"}
      [:button {:class "btn btn-default dropdown-toggle" :type "button" :id "dropdownMenu1" :data-toggle "dropdown" :aria-haspopup "true" :aria-expanded "true"}
       (or selected "Sort")
       [:span {:class "caret"}]]
      [:ul {:class "dropdown-menu" :aria-labelledby "dropdownMenu1"}
       [:li {:class "dropdown-header"}
        "Name"]
       (for [props (:name sort-option-data)]
         ^{:key (:label props)} [menu-option props])
       [:li {:role "separator" :class "divider"}]
       [:li {:class "dropdown-header"} "Price"]
       (for [props (:price sort-option-data)]
         ^{:key (:label props)} [menu-option props])
       [:li {:role "separator" :class "divider"}]
       [menu-option {:label "Clear" :facts {:db/id :transient :remove-entity :product-sort}}]]]]))

(defn sort-and-filter []
  [:div {:class "row filter-sort"}
   [filter-menu]
   [sort-menu]
   [:div {:class "col-sm-8"}]])

(defn product [{:keys [db/id product/name product/price hovered?]}]
    [:div {:class "col-sm-6 col-md-4"
           :on-mouse-enter #(then [:app :product-hovered id])
           :on-mouse-leave #(then [:app :product-hovered nil])}
     [:div {:class "product-tile"}
      [:div {:class "row"}
       [:div {:class "col-xs-8 product-name"}
        [:label name]]
       [:div {:class "col-xs-3 product-price"}
        [:label (format-price price)]]]
      [:div {:class "row"}
       [:div {:class "col-xs-4"}
        [:img {:src "http://placehold.it/100x100" :alt "..." :class "img-responsive product-image"}]]
       [:div {:class "col-xs-8"}
        [:p {:class "product-description"}]
        "This is the product description. It can be a summary as this is just
        the product tile."]]
      [:div {:class "row"}
       [:div {:class (if hovered? (str "col-xs-12") (str "col-xs-12 invisible"))}
        [:button {:class "btn btn-success add-to-cart"
                  :on-click #(then [:transient :add-to-cart id])}
         "ADD TO CART"]]]]])

(defn products []
  (let [{:keys [products hovered]} @(subscribe [:products])]
    [:div {:class "row"}
     (for [p products]
       ^{:key (:db/id p)} [product
                           (merge p {:hovered? (= (:db/id p) hovered)})])]))



(defn main-section []
  [:div {:class "container"}
   [:div {:class "row"}
    [intro]
    [sort-and-filter]
    [products]]])

(defn app []
  [:div {:class "root"}
   [header]
   [cart]
   [main-section]])
