(ns precept.todomvc.views
  (:require [precept.core :refer [subscribe then]]
            [reagent.core  :as reagent]))

(defn input [{:keys [value on-change on-key-down on-blur] :as props}]
  [:input
   (merge props
     {:type "text"
      :auto-focus true
      :value value
      :on-change on-change
      :on-key-down on-key-down})])

(defn todo-item []
  (fn [{:keys [db/id todo/title todo/edit todo/done]}]
    (println "Todo item render: id title, edit, done" id title edit done)
    [:li {:class (str (when done "completed ")
                      (when edit "editing"))}
      [:div.view
        [:input.toggle
          {:type "checkbox"
           :checked (if done true false)
           :on-change #(then [id :todo/done (not done)])}]
        [:label
          {:on-double-click #(then [id :todo/edit title])}
          title]
        [:button.destroy
          {:on-click #(then [:transient :remove-entity id])}]]
      (when edit
        [input
          {:class "edit"
           :value edit
           :on-change #(then [id :todo/edit (-> % .-target .-value)])
           :on-key-down #(then [:transient :input/key-code (.-which %)])
           :on-blur #(then [:transient :todo/save-edit id])}])]))

(defn task-list
  []
  (let [{:keys [visible-todos all-complete?]} @(subscribe [:task-list])]
       (prn "All visible in render" visible-todos)
       (prn "All complete?" all-complete?)
      [:section#main
        [:input#toggle-all
          {:type "checkbox"
           :checked (not all-complete?)
           :on-change #(then [:transient :mark-all-done true])}]
        [:label
          {:for "toggle-all"}
          "Mark all as complete"]
        [:ul#todo-list
          (for [todo visible-todos]
            ^{:key (:db/id todo)} [todo-item todo])]]))


(defn footer []
  (let [{:keys [active-count done-count visibility-filter]} @(subscribe [:footer])
        _ (println "[sub] Done count / active count in render" active-count done-count)
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
       [:button#clear-completed {:on-click #(then [:transient :clear-completed true])}
        "Clear completed"])]))


(defn task-entry []
  (let [{:keys [db/id entry/title]} @(subscribe [:task-entry])]
    [:header#header
      [:h1 "todos"]
      [input
        {:id "new-todo"
         :placeholder "What needs to be done?"
         :value title
         :on-key-down #(then [:transient :input/key-code (.-which %)])
         :on-change #(then [:global :entry/title (-> % .-target .-value)])}]]))

(defn todo-app []
  [:div
   [:section#todoapp
    [task-entry]
    [task-list]
    [footer]]
   [:footer#info
    [:p "Double-click to edit a todo"]]])
