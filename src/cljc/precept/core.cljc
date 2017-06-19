(ns ^:figwheel-always precept.core
    (:refer-clojure :exclude [send])
    (:require [precept.util :as util]
              [precept.listeners :as l]
              [precept.query :as q]
              [precept.state :refer [fact-id rules store state] :as s]
              [clara.rules :refer [fire-rules]]
              [precept.spec.core :refer [validate]]
              [precept.spec.sub :as sub]
              [precept.spec.lang :as lang]
      #?(:clj
              [clojure.core.async :refer [<! >! put! take! chan go go-loop]])
      #?(:clj
              [reagent.ratom :as rr])
      #?(:cljs [cljs.core.async :refer [put! take! chan <! >!]])
      #?(:cljs [reagent.core :as r]))
  #?(:cljs (:require-macros [cljs.core.async.macros :refer [go go-loop]])))

#?(:cljs (enable-console-print!))

(defn trace [& args]
  (comment (println args)))

(def groups [:action :calc :report :cleanup])
(def default-group :calc)

;; TODO. Pass namespace argument from define, rule.
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

(defn notify! [sub-name update-fn]
  (let [sub-id (:id (util/find-sub-by-name sub-name))]
    (swap! store update-in [sub-id ::sub/response] update-fn)))

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

(defn swap-session!
  [next]
  (trace "Swapping session!")
  (swap! state assoc :session next))

(defn swap-session-sync! [next f]
  (swap! state assoc :session next)
  (f))

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
            _ (trace "Ops!" ops)]
        (swap-session! next-session)
        (>! out ops)
        (recur)))
    out))

(defmulti apply-op-to-view-model!
  (fn [op a _ _]
    [op (first (clojure.set/intersection
                 #{:one-to-many :one-to-one}
                 (@s/ancestors-fn a)))]))

(defmethod apply-op-to-view-model! [:add :one-to-one] [_ _ ks v]
  (swap! s/store assoc-in ks v))

(defmethod apply-op-to-view-model! [:add :one-to-many] [_ _ ks v]
  (swap! s/store update-in ks conj v))

(defmethod apply-op-to-view-model! [:remove :one-to-one] [_ _ ks _]
  (swap! s/store util/dissoc-in ks))

(defmethod apply-op-to-view-model! [:remove :one-to-many] [_ _ ks v]
  (let [prop (get-in @s/store ks)
        applied (remove #(= v %) prop)]
    (if (empty? applied)
      (swap! s/store util/dissoc-in ks)
      (swap! s/store assoc-in ks applied))))

(defn apply-additions-to-view-model! [tuples]
  (doseq [[e a v] tuples]
    (if (not= a ::sub/response)
      (apply-op-to-view-model! :add a [e a] v)
      (doseq [[sub-a sub-v] v]
        (if (util/any-Tuple? sub-v)
          (apply-op-to-view-model! :add sub-a [e a sub-a] (util/Tuples->maps sub-v))
          (apply-op-to-view-model! :add sub-a [e a sub-a] sub-v))))))

(defn apply-removals-to-view-model! [tuples]
  (doseq [[e a v] tuples]
    (if (not= a ::sub/response)
      (apply-op-to-view-model! :remove a [e a] v)
      (doseq [[sub-a sub-v] v]
        (if (util/any-Tuple? sub-v)
          (apply-op-to-view-model! :remove sub-a [e a sub-a] (util/Tuples->maps sub-v))
          (apply-op-to-view-model! :remove sub-a [e a sub-a] sub-v))))))

(defn apply-removals-to-store
  "Reads ops from in channel and applies removals to store"
  [in]
  (let [out (chan)]
    (go-loop []
      (let [ops (<! in)
            _ (trace "Removals" (:removed ops))]
       (apply-removals-to-view-model! (remove util/impl-fact? (:removed ops)))
       (>! out ops)
       (recur)))
   out))

(defn apply-additions-to-store
  "Reads ops from channel and applies additions to store"
  [in]
  (go-loop []
    (let [ops (<! in)
          _ (trace "Additions" (:added ops))]
      (apply-additions-to-view-model! (remove util/impl-fact? (:added ops)))
      (>! done-ch :done)
      (recur)))
  nil)

(transactor)
(def realized-session (apply-changes-to-session session->store))
(def changes-out (read-changes-from-session realized-session))
(def removals-out (apply-removals-to-store changes-out))
(apply-additions-to-store removals-out)

;; TODO. Find equivalent in CLJ
(defn lens [a path]
  #?(:clj (atom (get-in @a path))
     :cljs (r/cursor a path)))

(defn register
  "Should only get called by `subscribe`, which determines if a sub exists.

  Generates an id used to track requests and responses throughout the system.

  `req` is a vector of name and params (currently we just support a name).
  Inserts subscription request into the current session. Responses are generated by subscription
  handler rules that match request name and insert facts into working memory.
  Subscriptions are stored in (:subscriptions @state). The lens is a reagent cursor that
  observes changes to the subscription's response that is written to the store."
  [req]
  (let [id (util/guid)
        name (first req)
        lens (lens store [id ::sub/response])]
    (dispatch! (fn [session] (util/insert session [id ::sub/request name])))
    (swap! state assoc-in [:subscriptions id] {:id id :name name :lens lens})
    lens))

(defn subscribe
  "Returns lens that points to a path in the store. Sub is handled by a rule."
  ([req]
   (let [name (first req)
         existing (util/find-sub-by-name name)
         _ (trace "New sub name / existing if any" name existing)]
     (or (:lens existing) (register req)))))

(defn then
  "Inserts facts into current session"
  [facts]
  (dispatch! (fn [session] (util/insert session facts))))

(defn start!
  "Initializes session with facts.

  - :session - the `session` from which changes will be tracked
  - :facts - initial facts

  Once initialized, facts are synced to a reagent ratom (`state/store`) and accessed via
  subscriptions.
  "
  [{:keys [session facts] :as options}]
  (let [opts (or options (hash-map))]
    (swap-session-sync!
      (l/replace-listener (:session opts))
      #(dispatch! (fn [session] (util/insert session (:facts opts)))))))

