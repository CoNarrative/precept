(ns libx.todomvc.subs
  (:require [libx.util]))

;
;(defn get-visibility-filter [session]
;  (:ui/visibility-filter (first (entities-where session :ui/visibility-filter))))
;
;(reg-sub :showing get-visibility-filter)
;
;(reg-sub :todos
;  (fn [session] (entities-where session :todo/title)))
;
;(reg-sub :visible-todos
;  (fn [session] (entities-where session :todo/visible)))
;
;(reg-sub :all-complete?
;  :<- [:todos]
;  (fn [todos _] (seq todos)))
;

