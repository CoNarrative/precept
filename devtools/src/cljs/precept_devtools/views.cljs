(ns precept-devtools.views
  (:require [reagent.core :as r]
            [precept-devtools.util :as util]))


(defn rule-item [rule]
  [:div (:display-name rule)])

(defn rule-list [rules]
  (let [user-rules (sort-by :type (remove util/impl-rule? rules))
        impl-rules (sort-by :type (filter util/impl-rule? rules))]
    [:div {:style {:display "flex" :flex-direction "column"}}
     (for [rule user-rules]
       ^{:key (:id rule)} [rule-item (util/display-text rule)])]))

(defn state-tree [store]
  [:div
   [:h4 "State tree"]
   [:div {:style {:display "flex" :justify-content "space-between"}}
    [:div "e"]
    [:div "a"]
    [:div "v"]]
   (map (fn [[e av]]
          ^{:key (str e)}
          [:div {:style {:display "flex"}}
           [:div {:style {:margin-right 15}}
            (subs (str e) 0 6)]
           [:div {:style {:display "flex" :flex 1 :flex-direction "column"}}
            (map
              (fn [[a v]]
                ^{:key (str e "-" a "-" (hash v))}
                [:div {:style {:min-width "100%" :display "flex" :justify-content "space-between"}}
                 [:div {:style {:flex 1}}
                  (str a)]
                 [:div {:style {:flex 1}}
                  (str v)]])
              av)]])
     store)])

(defn main-container [{:keys [rules store] :as precept-state}]
  [:div
   [:h4 "Rules"]
   [rule-list (vals @rules)]
   [state-tree @store]])


