(ns precept.state
    (:require [reagent.core :as r]))

(defn mk-ratom [args]
  #?(:clj (atom args) :cljs (r/atom args)))

(def initial-state
  {:session nil
   :session-history false ;; TODO. To enable/disable
   ;:session-history '() ;; TODO. Own atom (public).
   :subscriptions {}})

;; TODO. Aggregate impl. atoms into single `impl` atom
(def fact-id (atom -1))

(def fact-index (atom {}))

(def session-hierarchy (atom nil))

(def ancestors-fn (atom nil))

(def rules (atom []))

(defonce state (atom initial-state))
;; TODO. ----------------------------------------------

(defonce store (mk-ratom {}))

