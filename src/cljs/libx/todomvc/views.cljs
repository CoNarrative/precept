(ns libx.todomvc.views
  (:require [reagent.core  :as reagent]
            [libx.util :refer [guid]]
            [libx.core :as libx :refer [subscribe then]]))


(defn todo-input [{:keys [title on-save on-stop]}]
  (let [val (reagent/atom title)
        stop #(do (reset! val "")
                  (when on-stop (on-stop)))
        save #(let [v (-> @val str clojure.string/trim)]
               (when (seq v) (on-save v))
               (stop))]
    (fn [props]
      [:input (merge props
                     {:type "text"
                      :value @val
                      :auto-focus true
                      :on-blur save
                      :on-change #(reset! val (-> % .-target .-value))
                      :on-key-down #(case (.-which %)
                                     13 (save)
                                     27 (stop)
                                     nil)})])))

(defn todo-item []
  (let [editing (reagent/atom false)]
    (fn [{:keys [db/id todo/title todo/done]}]
      (println "Todo item render " title done)
      [:li {:class (str (when done "completed ")
                        (when @editing "editing"))}
        [:div.view
          [:input.toggle
            {:type "checkbox"
             :checked (if done true false)
             :on-change #(if done
                           (then :remove [id :todo/done :tag])
                           (then :add [id :todo/done :tag]))}]
          [:label
            {:on-double-click #(reset! editing true)}
            title]
          [:button.destroy
            {:on-click #(then :remove-entity id)}]]
        (when @editing
          [todo-input
            {:class "edit"
             :title title
             :on-save #(do (then :remove [id :todo/title title])
                           (then [id :todo/title %]))
             :on-stop #(reset! editing false)}])])))

(defn task-list
  []
  (let [{:keys [visible-todos all-complete?]} @(subscribe [:task-list])]
       (prn "All visible in render" visible-todos)
       (prn "All complete?" all-complete?)
      [:section#main
        [:input#toggle-all
          {:type "checkbox"
           :checked (not all-complete?)
           ;:on-change #(then [:ui/toggle-complete])} ;; TODO. Allow this?
           :on-change #(then [(guid) :ui/toggle-complete :tag])}]
        [:label
          {:for "toggle-all"}
          "Mark all as complete"]
        [:ul#todo-list
          (for [todo visible-todos]
            ^{:key (:db/id todo)} [todo-item todo])]]))


(defn footer-controls []
  (let [{:keys [active-count done-count visibility-filter]} @(subscribe [:footer])
        _ (println "[sub] Done count / active count in render" active-count done-count)
        _ (println "Test" (subscribe [:footer]))
        a-fn          (fn [filter-kw txt]
                        [:a {:class (when (= filter-kw visibility-filter) "selected")
                             :href (str "#/" (name filter-kw))} txt])]
    [:footer#footer
     [:span#todo-count
      [:strong active-count] " " (case active-count 1 "item" "items") " left"]
     [:ul#filters
      [:li (a-fn :all    "All")]
      [:li (a-fn :active "Active")]
      [:li (a-fn :done   "Completed")]]
     (when (pos? done-count)
       [:button#clear-completed {:on-click #(then [(guid) :ui/clear-completed :tag])}
        "Clear completed"])]))


(defn task-entry
  []
  [:header#header
    [:h1 "todos"]
    [todo-input
      {:id "new-todo"
       :placeholder "What needs to be done?"
       :on-save #(then [(random-uuid) :todo/title %])}]])

(defn todo-app
  []
  [:div
   [:section#todoapp
    [task-entry]
    (when (seq @(subscribe [:todo-app]))
      [task-list])
    [footer-controls]]
   [:footer#info
    [:p "Double-click to edit a todo"]]])
