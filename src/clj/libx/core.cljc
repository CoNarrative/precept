(ns ^:figwheel-always libx.core
    (:refer-clojure :exclude [send])
    (:require [libx.util :refer [entity-tuples->entity-map] :as util]
              [libx.listeners :as l]
              [libx.schema :as schema]
              [libx.query :as q]
              [clara.rules :refer [query fire-rules insert! insert-all!] :as cr]
              [clara.rules.accumulators :as acc]
              [libx.spec.core :refer [validate]]
              [libx.spec.sub :as sub]
              [libx.spec.lang :as lang]
              [libx.tuplerules :refer [def-tuple-session def-tuple-rule def-tuple-query]]
      #?(:clj [clojure.core.async :refer [<! >! put! take! chan go go-loop]])
      #?(:clj [reagent.ratom :as rr])
      #?(:cljs [cljs.core.async :refer [put! take! chan <! >!]])
      #?(:cljs [reagent.core :as r]))
  #?(:cljs (:require-macros [cljs.core.async.macros :refer [go go-loop]])))

#?(:cljs (enable-console-print!))

(defn log [& args]
  (comment (println args)))

(defonce initial-state
  {:subscriptions {}
   :session nil
   :schema nil
   :session-history (seq nil)
   :transitioning nil
   :pending-updates []})

(defonce state (atom initial-state))

(defn mk-ratom [args]
  #?(:clj (atom args) :cljs (r/atom args)))

(defonce store (mk-ratom {}))

(defn init-schema [schema]
  (swap! state assoc :schema (schema/by-ident schema)))

(def processing (chan 1))
(def session->store (chan 1))
(def done-ch (chan 1))

(defn update-session-history
  "First history entry is most recent"
  [session]
  (if (= 5 (count (:session-history @state)))
    (swap! state update :session-history
      (fn [sessions] (conj (butlast sessions) session)))
    (swap! state update :session-history conj session)))

(defn set-transition [bool]
  (log "---> Transitioning state" bool)
  (swap! state assoc :transitioning bool))

(defn swap-session! [next]
  (log "Swapping session!")
  (swap! state assoc :session next))

(defn enqueue-update [f]
  (log "Enqueueing update. Cur / all" f (:pending-updates @state))
  (swap! state update :pending-updates conj f))

(defn dequeue-update []
  (log "Dequeueing update")
  (swap! state update :pending-updates (fn [updates] (rest updates))))

(defn dispatch! [f]
  (enqueue-update f)
  (put! processing f))

(defn debug-id []
  (hash (first (:pending-updates @state))))

(defn transactor []
  (go-loop []
   (let [processing (<! processing)]
      (if (:transitioning @state)
        (do (log "---> Looks like we're transitioning? I'll wait to process" (debug-id))
            (do (<! done-ch)
                (log "---> Hey, we're done! Check if we can process" (debug-id))
                (recur)))
        (if (empty? (:pending-updates @state))
            (do (log "--->  No more pending updates.") (recur)) ;;should be able to start here?
            (do (log " ---> Kicking off!" (debug-id))
                (set-transition true)
                (>! session->store (first (:pending-updates @state)))
                (dequeue-update)
                (<! done-ch)
                (recur)))))))

(defn apply-changes-to-session [in]
  (let [out (chan)]
    (go-loop []
      (let [f (<! in)
            applied (f (:session @state))
            fired (fire-rules applied)]
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
            _ (log "Ops!" ops)]
        ;(update-session-history session)
        (swap-session! next-session)
        (>! out ops)
        (recur)))
    out))

(defn add [a change]
  "Merges change into atom"
  (log "Adding" (:db/id change) (l/change->av-map change))
  (swap! a update (:db/id change) (fn [ent] (merge ent (l/change->av-map change)))))

(defn del [a change]
  "Removes keys in change from atom"
  (log "Removing in path" (:db/id change) (l/change->attr change))
  (let [id (:db/id change)
        attr (l/change->attr change)]
    (swap! a util/dissoc-in [id attr])))

(defn apply-removals-to-store [in]
  "Reads ops from in channel and applies removals to store"
  (let [out (chan)]
    (go-loop []
      (let [ops (<! in)
            removals (l/embed-op (:removed ops) :remove)
            _ (log "Removals?" removals)]
       (doseq [removal removals]
          (del store removal))
       (>! out ops)
       (recur)))
   out))

(defn apply-additions-to-store [in]
  "Reads ops from channel and applies additions to store"
  (go-loop []
    (let [changes (<! in)
          additions (l/embed-op (:added changes) :add)
          _ (log "Additions!" additions)]
      (doseq [addition additions]
        (add store addition))
      (set-transition false)
      (>! done-ch :hi)
      (recur)))
  nil)

(transactor)
(def realized-session (apply-changes-to-session session->store))
(def changes-out (read-changes-from-session realized-session))
(def removals-out (apply-removals-to-store changes-out))
(def addition-applier (apply-additions-to-store removals-out))

(defn unique-identity-attrs [schema tuples]
  (reduce (fn [acc cur]
           (if (schema/unique-attr? schema (second cur))
             (conj acc (second cur))
             acc))
    [] tuples))

(defn unique-value-attrs [schema tuples]
  (reduce (fn [acc cur]
            (if (schema/unique-value? schema (second cur))
              (conj acc (second cur))
              acc))
    [] tuples))

(defn unique-value-facts [session tups unique-attrs]
  (let [unique-tups (filter #((set unique-attrs) (second %)) tups)
        avs (map rest unique-tups)]
    (mapcat (fn [[a v]] (q/facts-where session a v))
      avs)))


;; 1. Decide whether we require a schema in defsession
;; If we do...
;; 2. Check schema is not nil in state
;; 3. For each attr in facts
;;    * if unique,
;;        remove all facts with unique attr and insert new fact
;;        else insert new fact
;;TODO. Rename/move
(defn schema-insert
  "Inserts each fact according to conditions defined in schema.
  Currently supports: db.unique/identity, db.unique/value"
  [session facts]
  (let [schema (:schema @state)
        facts-v (if (coll? (first facts)) facts (vector facts))
        tuples (mapcat util/tuplize facts-v)
        unique-attrs (unique-identity-attrs schema tuples)
        unique-values (unique-value-attrs schema tuples)
        existing-unique-identity-facts (mapcat #(q/facts-where session %)
                                         unique-attrs)
        existing-unique-value-facts (unique-value-facts session tuples unique-values)
        existing-unique-facts (into existing-unique-identity-facts existing-unique-value-facts)
        _ (log "Schema-insert unique values " unique-values)
        _ (log "Schema-insert unique value facts " existing-unique-value-facts)
        _ (log "Schema-insert removing " existing-unique-facts)
        _ (log "Schema-insert inserting " tuples)
        next-session (if (empty? existing-unique-facts)
                       (-> session
                         (util/insert tuples))
                       (-> session
                         (util/retract existing-unique-facts)
                         (util/insert tuples)))]
    next-session))

(defn insert-action [facts]
  (fn [current-session]
    (schema-insert current-session facts)))

(defn retract-action [facts]
  (fn [current-session]
    (util/retract current-session facts)))

;; TODO. Find equivalent in CLJ
(defn lens [a path]
  #?(:clj (atom (get-in @a path))
     :cljs (r/cursor a path)))

(defn register
  [req]
  "Should only be called by `subscribe`. Will register a new subscription!
  Generates a subscription id used to track the req and its response throughout the system.
  Req is a vector of name and params (currently we just support a name).
  The request is inserted as a fact into the rules session where a rule should handle the request
  the request by matching on the request name and inserting a response in the RHS.
  Writes subscription to state -> subscriptions. The lens is a reagent cursor that
  observes changes to the subscription's response that is written to the store."
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
   (let [_ (validate ::sub/request req) ;;TODO. move to :pre
         name (first req)
         existing (find-sub-by-name name)
         _ (log "New sub name / existing if any" name existing)]
     (if existing
       (:lens existing)
       (register req)))))

(defn then
  ([op facts]
   (condp = op
     :add (dispatch! (insert-action facts))
     :remove (dispatch! (retract-action facts))
     :remove-entity (dispatch! (insert-action [(util/guid) :remove-entity-request facts]))
     (log "Unsupported op keyword " op))
   nil)
  ([facts] (then :add facts)))

(defn start! [options]
  (let [opts (or options (hash-map))]
    (swap-session! (l/replace-listener (:session opts)))
    (init-schema (into schema/libx-schema (:schema opts)))
    (dispatch! (insert-action (:facts opts)))))

