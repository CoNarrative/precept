(ns libx.todomvc.subs
  (:require [re-frame.core :refer [reg-sub subscribe]]
            [clara.rules :refer [query]]
            [libx.util :refer [entities-where]]
            [libx.todomvc.rules :refer [find-done-count]]))


(defn get-visibility-filter [session]
  (:ui/visibility-filter (first (entities-where session :ui/visibility-filter))))

;(defn get-done-count [session]
;  (let [done-count (:?count (first (query session find-done-count)))]
;    (println "Got done count" done-count)
;    (or done-count 0)))

(reg-sub :showing get-visibility-filter)

(reg-sub :todos
  (fn [session] (entities-where session :todo/title)))

(reg-sub :visible-todos
  (fn [session] (entities-where session :todo/visible)))

(reg-sub :all-complete?
  :<- [:todos]
  (fn [todos _] (seq todos)))

;(reg-sub :completed-count get-done-count)

(reg-sub :footer-counts
  (fn [] [123456]))
  ;:<- [:todos]
  ;:<- [:completed-count]
  ;(fn [[todos completed] _]
  ;  [(- (count todos) completed) completed])
