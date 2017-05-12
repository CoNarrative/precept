(ns libx.impl.rules
  (:require [clara.rules :as cr]))


(cr/defrule action-cleanup___impl
  {:group :cleanup}
  [?action <- :action]
  =>
  (cr/retract! ?action))

