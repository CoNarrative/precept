(ns libx.todomvc.views
  (:require [reagent.core  :as reagent]
            [libx.core :as libx :refer [subscribe send]]))


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

(defn todo-item
  []
  (let [editing (reagent/atom false)]
    (fn [{:keys [db/id todo/title todo/done]}]
      [:li {:class (str (when done "completed ")
                        (when @editing "editing"))}
        [:div.view
          [:input.toggle
            {:type "checkbox"
             :checked done
             :on-change #(send (if done
                                 [:remove [id :todo/done]]
                                 [id :toggle-done :tag]))}]
          [:label
            {:on-double-click #(reset! editing true)}
            title]
          [:button.destroy
            {:on-click #(send :remove id)}]]
        (when @editing
          [todo-input
            {:class "edit"
             :title title
             :on-save #(send 'retract [id :todo/title title]
                             'insert [id :todo/title %])
             :on-stop #(reset! editing false)}])])))

(defn task-list
  []
  (let [{:keys [visible-todos all-complete?]} @(libx/subscribe :component/task-list)]
       (prn "all visible in render" visible-todos)
      [:section#main
        [:input#toggle-all
          {:type "checkbox"
           :checked all-complete?
           :on-change #(send :ui/toggle-complete)}]
        [:label
          {:for "toggle-all"}
          "Mark all as complete"]
        [:ul#todo-list
          (for [todo visible-todos]
            ^{:key (:db/id todo)} [todo-item todo])]]))


(defn footer-controls []
  (let [{:keys [active-count done-count ui/visibility-filter] :as props}
        @(libx/subscribe [:component/footer-controls])
        _ (println "[sub] Active done in render" active-count done-count)
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
       [:button#clear-completed {:on-click #(dispatch [:clear-completed])}
        "Clear completed"])]))


(defn task-entry
  []
  [:header#header
    [:h1 "todos"]
    [todo-input
      {:id "new-todo"
       :placeholder "What needs to be done?"
       :on-save #(send [:add-todo %])}]])


(defn todo-app
  []
  [:div
   [:section#todoapp
    [task-entry]
    (when (seq @(libx/subscribe [:component/todo-app]))
      [task-list])
    [footer-controls]]
   [:footer#info
    [:p "Double-click to edit a todo"]]])
