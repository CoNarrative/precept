(ns fullstack.db.data
    (:require [clojure.string :as string]
              [precept.util :as util]))


(def data {:adjective ["Small" "Ergonomic" "Rustic" "Intelligent" "Gorgeous" "Incredible" "Fantastic"
                       "Practical" "Sleek" "Awesome" "Generic" "Handcrafted" "Handmade" "Licensed"
                       "Refined" "Unbranded" "Tasty"]
           :material  ["Steel" "Wooden" "Concrete" "Plastic" "Cotton" "Granite" "Rubber" "Metal"
                       "Soft" "Fresh" "Frozen"]
           :product   ["Chair" "Car" "Computer" "Keyboard" "Mouse" "Bike" "Ball" "Gloves" "Pants"
                       "Shirt" "Table" "Shoes" "Hat" "Towels" "Soap" "Tuna" "Chicken" "Fish" "Cheese"
                       "Bacon" "Pizza" "Salad" "Sausages" "Chips"]})


(defn mk-product []
  {:db/id (java.util.UUID/randomUUID)
   :product/name (string/join " "
                   (mapv (fn [kw] (rand-nth (kw data)))
                     (keys data)))
   :product/price (str (format "%.2f" (rand 100)))})
