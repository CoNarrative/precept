(ns precept.state
    #?(:cljs (:require [reagent.core :as r])))

(defn mk-ratom [args]
  #?(:clj (atom args)
     :cljs (r/atom args)))

(def initial-state
  {:session nil
   :session-history false ;; TODO. To enable/disable
   ;:session-history '() ;; TODO. Own atom (public).
   :subscriptions {}})

(def fact-id (atom -1))

(def fact-index (atom {}))

(def schemas (atom {}))

(def session-hierarchy (atom nil))

(def ancestors-fn (atom nil))

(def rules (atom {}))

(def rule-files (atom #{}))

(def session-defs (atom {}))

(def unconditional-inserts (atom #{}))

(def state (atom initial-state))

(defonce store (mk-ratom {}))

