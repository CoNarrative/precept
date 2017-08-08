(ns precept.state
    #?(:cljs (:require [reagent.core :as r])))

(defn mk-ratom [args]
  #?(:clj (atom args)
     :cljs (r/atom args)))

(defonce initial-state
  {:session nil
   :session-history false
   ;:session-history '()
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

(def *devtools (atom {}))

(def *event-coords
  (atom {:event-number 0
         :state-number 0
         :state-id #?(:clj (java.util.UUID/randomUUID)
                      :cljs (random-uuid))}))

(def state (atom initial-state))

(defonce store (mk-ratom {}))

