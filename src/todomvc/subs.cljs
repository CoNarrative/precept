(ns todomvc.subs
  (:require [re-frame.core :refer [reg-sub subscribe]]
            [clara.rules :refer [query]]
            [todomvc.rules :refer [entities-where
                                   find-done-count]]
            [todomvc.events :refer [get-todos]]))

(defn get-visibility-filter [db]
  (:ui/visibility-filter
    (first (entities-where db :ui/visibility-filter))))

(reg-sub :showing get-visibility-filter)

(reg-sub :todos
  (fn [session] (entities-where session :todo/title)))

(defn get-visible-todos [session]
  (entities-where session :todo/visible true))
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
