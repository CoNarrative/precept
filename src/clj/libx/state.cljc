(ns libx.state
    (:require [reagent.core :as r]))

(defn mk-ratom [args]
  #?(:clj (atom args) :cljs (r/atom args)))

(def initial-state
  {:session nil
   :session-history '()
   :subscriptions {}})

(def fact-id (atom -1))

(def rules (atom []))

(def session-hierarchy (atom nil))

(defonce store (mk-ratom {}))

(defonce state (atom initial-state))
