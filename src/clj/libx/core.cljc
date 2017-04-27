(ns ^:figwheel-always libx.core
    (:refer-clojure :exclude [send])
    (:require [libx.util :as util]
              [libx.listeners :as l]
              [libx.schema :as schema]
              [libx.query :as q]
              [clara.rules :refer [fire-rules]]
              [libx.spec.core :refer [validate]]
              [libx.spec.sub :as sub]
              [libx.spec.lang :as lang]
      #?(:clj [clojure.core.async :refer [<! >! put! take! chan go go-loop]])
      #?(:clj [reagent.ratom :as rr])
      #?(:cljs [cljs.core.async :refer [put! take! chan <! >!]])
      #?(:cljs [reagent.core :as r]))
  #?(:cljs (:require-macros [cljs.core.async.macros :refer [go go-loop]])))

#?(:cljs (enable-console-print!))

(defn trace [& args]
  (comment (println args)))

(def rules (atom []))

(defn register-rule [type lhs rhs]
  "Returns rule name if found in registry, else registers new rule and returns name"
  (if-let [existing (first (filter #(and (= rhs (:consequences %)) (= lhs (:conditions %))) @rules))]
    (:name existing)
    (let [id (util/guid)
          entry {:id id
                 :type type
                 :name (str type "-" id)
                 :conditions lhs
                 :consequences rhs}]
      (swap! rules conj entry)
      (:name entry))))

(def fact-id (atom -1))

(def initial-state
  {:session nil
   :session-history '()
   :subscriptions {}})

(defonce state (atom initial-state))

(defn mk-ratom [args]
  #?(:clj (atom args) :cljs (r/atom args)))

(defonce store (mk-ratom {}))

(defn init-schema [schema]
  (swap! state assoc :schema (schema/by-ident schema)))

(def action-ch (chan 1))
(def session->store (chan 1))
(def done-ch (chan 1))

(defn update-session-history
  "First history entry is most recent"
  [session]
  (if (= 5 (count (:session-history @state)))
    (swap! state update :session-history
      (fn [sessions] (conj (butlast sessions) session)))
    (swap! state update :session-history conj session)))

(defn swap-session! [next]
  (trace "Swapping session!")
  (swap! state assoc :session next))

(defn dispatch! [f] (put! action-ch f))

(defn transactor []
  (go-loop []
   (let [action (<! action-ch)]
        (do (trace " ---> Kicking off!" (hash action))
            (>! session->store action)
            (<! done-ch)
            (recur)))))

(defn apply-changes-to-session [in]
  (let [out (chan)]
    (go-loop []
      (let [f (<! in)
            applied (f (:session @state))
            fired (time (fire-rules applied))]
        (>! out fired)
        (recur)))
    out))

(defn read-changes-from-session
  "Reads changes from session channel, fires rules, and puts resultant changes
  on changes channel. Updates session state atom with new session."
  [in]
  (let [out (chan)]
    (go-loop []
      (let [session (<! in)
            ops (l/vec-ops session)
            next-session (l/replace-listener session)
            _ (trace "Ops!" ops)]
        (swap-session! next-session)
        (>! out ops)
        (recur)))
    out))

(defn add
  "Merges change into atom"
  [a change]
  (trace "Adding" (:db/id change) (l/change->av-map change))
  (swap! a update (:db/id change) (fn [ent] (merge ent (l/change->av-map change)))))

(defn del
  "Removes keys in change from atom"
  [a change]
  (trace "Removing in path" (:db/id change) (l/change->attr change))
  (let [id (:db/id change)
        attr (l/change->attr change)]
    (swap! a util/dissoc-in [id attr])))

(defn apply-removals-to-store
  "Reads ops from in channel and applies removals to store"
  [in]
  (let [out (chan)]
    (go-loop []
      (let [ops (<! in)
            removals (l/embed-op (:removed ops) :remove)
            _ (trace "Removals" removals)]
       (doseq [removal removals]
          (del store removal))
       (>! out ops)
       (recur)))
   out))

(defn apply-additions-to-store
  "Reads ops from channel and applies additions to store"
  [in]
  (go-loop []
    (let [changes (<! in)
          additions (l/embed-op (:added changes) :add)
          _ (trace "Additions!" additions)]
      (doseq [addition additions]
        (add store addition))
      (>! done-ch :done)
      (recur)))
  nil)

(transactor)
(def realized-session (apply-changes-to-session session->store))
(def changes-out (read-changes-from-session realized-session))
(def removals-out (apply-removals-to-store changes-out))
(apply-additions-to-store removals-out)

(defn insert-action [facts]
  (fn [current-session]
    (util/insert current-session facts)))

(defn retract-action [facts]
  (fn [current-session]
    (util/retract current-session facts)))

;; TODO. Find equivalent in CLJ
(defn lens [a path]
  #?(:clj (atom (get-in @a path))
     :cljs (r/cursor a path)))

(defn register
  "Should only be called by `subscribe`. Will register a new subscription!
  Generates a subscription id used to track the req and its response throughout the system.
  Req is a vector of name and params (currently we just support a name).
  The request is inserted as a fact into the rules session where a rule should handle the request
  the request by matching on the request name and inserting a response in the RHS.
  Writes subscription to state -> subscriptions. The lens is a reagent cursor that
  observes changes to the subscription's response that is written to the store."
  [req]
  (let [id (util/guid)
        name (first req)
        lens (lens store [id ::sub/response])]
    (dispatch! (insert-action [id ::sub/request name]))
    (swap! state assoc-in [:subscriptions id] {:id id :name name :lens lens})
    lens))

(defn find-sub-by-name [name]
  (second
    (first
      (filter
        (fn [[id sub]] (= name (:name sub)))
        (:subscriptions @state)))))

(defn subscribe
  "Returns lens that points to a path in the store. Sub is handled by a rule."
  ([req]
   (let [;_ (validate ::sub/request req) ;;TODO. move to :pre
         name (first req)
         existing (find-sub-by-name name)
         _ (trace "New sub name / existing if any" name existing)]
     (or (:lens existing) (register req)))))

(defn then
  ([op facts]
   (condp = op
     :add (dispatch! (insert-action facts))
     :remove (dispatch! (retract-action facts))
     :remove-entity (dispatch! (insert-action [(util/guid) :remove-entity-request facts]))
     (trace "Unsupported op keyword " op))
   nil)
  ([facts] (then :add facts)))

(defn start! [options]
  (let [opts (or options (hash-map))]
    (swap-session! (l/replace-listener (:session opts)))
    (init-schema (into schema/libx-schema (:schema opts)))
    (dispatch! (insert-action (:facts opts)))))

