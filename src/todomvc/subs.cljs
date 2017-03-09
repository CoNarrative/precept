(ns todomvc.subs
  (:require [re-frame.core :refer [reg-sub subscribe]]
            [clara.rules :refer [query]]
            [todomvc.rules :refer [find-showing
                                   find-visible-todos
                                   find-done-count]]
            [todomvc.events :refer [get-todos]]))

(defn get-showing [db]
  (let [showing (:key (:?showing (first (query db find-showing))))]
    (prn "showing" showing)
    showing))
(reg-sub :showing get-showing)

(reg-sub :todos get-todos)

(defn get-visible-todos [session]
  (:todos (:?visible-todos (first (query session find-visible-todos)))))
(reg-sub :visible-todos get-visible-todos)

(reg-sub
  :all-complete?
  :<- [:todos]
  (fn [todos _]
    (seq todos)))

(defn get-done-count [session]
  (or (:?count (first (query session find-done-count)))
    0))
(reg-sub :completed-count get-done-count)

(reg-sub
  :footer-counts
  :<- [:todos]
  :<- [:completed-count]
  (fn [[todos completed] _]
    [(- (count todos) completed) completed]))
