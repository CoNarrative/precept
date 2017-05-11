(ns libx.todomvc.views
  (:require [libx.core :refer [subscribe then]]
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
           :on-change #(then :todo/toggle-done-action {:id id
                                                       :old-val done})}]
        [:label
          {:on-double-click #(then :todo/start-edit-action {:id id})}
          title]
        [:button.destroy
          {:on-click #(then :remove-entity-action {:id id})}]]
      (when edit
        [input
          {:class "edit"
           :value edit
           :on-change #(then :todo/update-edit-action {:id id :value (-> % .-target .-value)})
           :on-key-down #(then :input/key-code-action {:input/key-code (.-which %)})
           :on-blur #(then :todo/save-edit-action {:id id})}])]))

(defn task-list
  []
  (let [{:keys [visible-todos all-complete?]} @(subscribe [:task-list])]
       (prn "All visible in render" visible-todos)
       (prn "All complete?" all-complete?)
      [:section#main
        [:input#toggle-all
          {:type "checkbox"
           :checked (not all-complete?)
           :on-change #(then :ui/mark-all-done-action)}]
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
       [:button#clear-completed {:on-click #(then :ui/clear-completed-action)}
        "Clear completed"])]))


(defn task-entry []
  (let [{:keys [db/id entry/title]} @(subscribe [:task-entry])]
    [:header#header
      [:h1 "todos"]
      [input
        {:id "new-todo"
         :placeholder "What needs to be done?"
         :value title
         :on-key-down #(then :input/key-code-action {:input/key-code (.-which %)})
         :on-change #(then :entry/title-action {:entry/title (-> % .-target .-value)})}]]))

(defn todo-app []
  [:div
   [:section#todoapp
    [task-entry]
    [task-list]
    [footer]]
   [:footer#info
    [:p "Double-click to edit a todo"]]])
