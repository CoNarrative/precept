(ns fullstack.devtools
  (:require [precept.state :as state]
            [reagent.core :as r]))


(defmulti display-text :type)

(defmethod display-text "define" [m]
  (-> (update m :display-name #(str (vec (rest (first (:rhs m))))))
    (update :lhs str)))

(defmethod display-text :default [m]
  (-> (update m :display-name #(str (:name m)))
    (update :lhs str)))

(defn impl-rule? [m]
  (clojure.string/includes? (str (:name m)) "___impl"))

;;;; Views

(defn rule-item [rule]
  [:div (:display-name rule)])

(defn rule-list [rules]
  (let [user-rules (sort-by :type (remove impl-rule? rules))
        impl-rules (sort-by :type (filter impl-rule? rules))]
    [:div {:style {:display "flex" :flex-direction "column"}}
     (for [rule user-rules]
       ^{:key (:id rule)} [rule-item (display-text rule)])]))

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

;; From figwheel
(defn node [t attrs & children]
  (let [e (.createElement js/document (name t))]
    (doseq [k (keys attrs)] (.setAttribute e (name k) (get attrs k)))
    (doseq [ch children] (.appendChild e ch)) ;; children
    e))

(defn get-or-create-mount-node! [mount-node-id]
  (if-not (.getElementById js/document mount-node-id)
    (let [mount-node (node :div
                       {:id mount-node-id
                        :style
                            (str "-webkit-transition: all 0.2s ease-in-out;"
                              "-moz-transition: all 0.2s ease-in-out;"
                              "-o-transition: all 0.2s ease-in-out;"
                              "transition: all 0.2s ease-in-out;"
                              "font-size: 13px;"
                              "border-top: 1px solid #f5f5f5;"
                              "box-shadow: 0px 0px 1px #aaaaaa;"
                              "line-height: 18px;"
                              "color: #333;"
                              "font-family: monospace;"
                              "padding: 0px 10px 0px 70px;"
                              "position: fixed;"
                              "bottom: 0px;"
                              "left: 0px;"
                              "height: 100%;"
                              "width: 60%;"
                              "opacity: 1.0;"
                              "overflow: scroll;"
                              "background: aliceblue;"
                              "box-sizing: border-box;"
                              "z-index: 9999;"
                              "text-align: left;")})]
      (do (-> (.-body js/document) (.appendChild mount-node))
          (.getElementById js/document mount-node-id)))
    (.getElementById js/document mount-node-id)))

(defn render!
  ([{:keys [rules store] :as precept-state}]
   (let [mount-node-id "precept-devtools"
         mount-node (get-or-create-mount-node! mount-node-id)]
     (r/render [main-container {:rules rules :store store}]
               mount-node))))
