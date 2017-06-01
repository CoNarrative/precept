(ns precept.draw.rules
  (:require-macros [precept.dsl :refer [<- entity entities]])
  (:require [precept.accumulators :as acc]
            [precept.spec.error :as err]
            [crate.core :refer [html]]
            [precept.util :refer [insert! insert-unconditional! retract! guid] :as util]
            [precept.rules :refer-macros [define defsub session rule]]
            [precept.draw.schema :as schema]
            [precept.draw.facts :refer [todo entry done-count active-count visibility-filter]]))


;;;;;;;;;;;;; DOM Manipulation Helpers ;;;;;;;;;;;;;;;;;;

(defn by-id [id]
  (try
    (.getElementById js/document id)
    (catch js/Object e (.error js/console (str "Could not find element with id " id) e))))

(defn append-new [target new]
  (.appendChild (by-id target) new))

(defn set-style!
  [elem m]
  (let [style (.-style elem)]
    (doseq [[k v] m]
      (.setProperty style (name k) v))
    elem))

(defn as-nsless-keyword
  [kw]
  (-> (str kw)
    (clojure.string/split "/")
    (second)
    (keyword)))

(defn attrs
  [entity-avs]
  (reduce
    (fn [acc [a v]]
      (if (clojure.string/includes? (str a) "attr")
        (conj acc (vector (as-nsless-keyword a) v))
        acc))
    []
    entity-avs))

(defn hit-node [event]
  "Takes .path property of a DOM event and returns first element with an id"
  (first (filter #(not (clojure.string/blank? (.-id %))) (.-path event))))


;;;;;;;;;;;;; Application Rules ;;;;;;;;;;;;;;;;;;

(rule intercept-mouse-down
  {:group :action}
  [[_ :mouse/down ?event]]
  =>
  (.log js/console "Mouse down event" (hit-node ?event)))

(rule intercept-key-down
  {:group :action}
  [[_ :key-down/key-code ?v]]
  =>
  (println "Keycode " ?v))

(rule detect-hit
  {:group :action}
  [[_ :mouse/down ?event]]
  [:test (not (nil? (hit-node ?event)))]
  =>
  (let [node (hit-node ?event)]
    (insert! [[:transient :hit/node node]
              [:transient :hit/id (.-id node)]])))

(rule make-hit-nodes-red
  [[_ :hit/node ?node]]
  =>
  (set-style! ?node {:background-color "red"}))


;; TODO. We want to always establish facts instead of immediately performing a dom operation
(define ["root" :attr/cursor "crosshair"] :- [[_ :hit/id "root"]])

;(set-style! (by-id "root") {:cursor "crosshair"})

(rule intercept-mouse-up
  {:group :action}
  [[_ :mouse/up ?event]]
  =>
  (println "Mouse up event" ?event))


;;;;;;;;;;;;;; Framework Rules ;;;;;;;;;;;;;;;;;;;;

(rule append-element
  {:group :action}
  [[:transient :command :create-element]]
  [[?e :elem/tag ?tag]]
  [[?container :contains ?e]]
  [(<- ?ent (entity ?e))]
  =>
  (let [avs (mapv (juxt :a :v) ?ent)]
    (append-new
      ?container
      (html [?tag (apply hash-map (flatten (attrs avs)))]))))


(rule remove-orphaned-when-unique-conflict
  [[?e ::err/type :unique-conflict]]
  [[?e ::err/failed-insert ?v]]
  [?orphaned <- [(:e ?v) :all]]
  =>
  (retract! ?orphaned))

(session app-session
  'precept.draw.rules
  :db-schema schema/db-schema
  :client-schema schema/client-schema)
