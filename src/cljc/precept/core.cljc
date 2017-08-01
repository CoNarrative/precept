(ns precept.core
    (:require [precept.util :as util]
              [precept.listeners :as l]
              [precept.state :refer [fact-id rules store state] :as s]
              [clara.rules :refer [fire-rules]]
              [precept.spec.core :refer [validate]]
              [precept.spec.sub :as sub]
              [precept.spec.lang :as lang]
      #?(:clj [clojure.core.async :refer [<! >! put! take! chan go go-loop] :as async])
      #?(:cljs [cljs.core.async :refer [put! take! chan <! >!] :as async])
      #?(:cljs [reagent.core :as r]))
  #?(:cljs (:require-macros [cljs.core.async.macros :refer [go go-loop]])))

#?(:cljs (enable-console-print!))

(defn trace [& args]
  (comment (println args)))

(def groups [:action :calc :report :cleanup])
(def default-group :calc)

(defn matching-consequences [rhs rule-index]
  (filter #(= rhs (:rhs %)) (vals rule-index)))

(defn matching-conditions-and-consequences
  [lhs rhs rules-index]
  (filter #(and (= rhs (:rhs %))
                (= lhs (:lhs %)))
    (vals rules-index)))

(defmulti register-rule
  "Returns rule name if found in registry, else registers new rule and returns name"
  :type)

(defn identical-conditions-and-consequences-error
  [{:keys [existing-name type]}]
  (throw
    (ex-info (str "Found " type
               " with same conditions and consequences as existing definition: "
               "Existing name: " existing-name)
      {})))

(defmethod register-rule "define" [{:keys [_ ns type lhs rhs]}]
  (if-let [existing (first (matching-conditions-and-consequences lhs rhs @rules))]
    ;(do (identical-conditions-and-consequences-error
    ;      {:existing-name (:name existing)
    ;       :type "define"
    ;       :existing-conditions (:conditions existing)
    ;       :existing-consequences (:consequences existing)
    ;       :new-conditions lhs
    ;       :new-consequences rhs]
    (symbol (:name existing))
    (let [id (str (hash (str lhs rhs)))
          name (symbol (str "define-" id))
          entry {:id id
                 :type type
                 :name name
                 :ns ns
                 :lhs lhs
                 :rhs rhs}]
      (swap! rules assoc name entry)
      (symbol (:name entry)))))

(defmethod register-rule :default [{:keys [name ns type lhs rhs]}]
  (if-let [existing (get rules name)]
    ;(println (str "Found " type " with same conditions and consequences as existing definition: "
    (:name existing)
    (let [id (util/guid)
          entry {:id id
                 :type type
                 :name name
                 :ns ns
                 :lhs lhs
                 :rhs rhs}]
      (swap! rules assoc name entry)
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
  (if (= 5 (count (:session-history @s/state)))
    (swap! s/state update :session-history
      (fn [sessions] (conj (butlast sessions) session)))
    (swap! s/state update :session-history conj session)))

(defn swap-session!
  [next]
  (trace "Swapping session!")
  (swap! s/state assoc :session next))

(defn swap-session-sync! [next f]
  (swap! s/state assoc :session next)
  (f)
  (:session @s/state))

(defn dispatch! [f] (put! action-ch f))

(defn transactor []
  (go-loop []
   (let [action (<! action-ch)]
        (do (>! session->store action)
            (<! done-ch)
            (recur)))))

(defn apply-changes-to-session [in]
  (let [out (chan)]
    (go-loop []
      (let [f (<! in)
            applied (f (:session @s/state))
            fired (fire-rules applied)]
        (>! out fired)
        (recur)))
    out))

(defn read-changes-from-session
  "Reads changes from session channel, fires rules, and puts resultant changes
  on changes channel. Updates session state atom with new session. Changes are returned
  keyed by :added, :removed as Tuple records."
  [in]
  (let [out (chan)]
    (go-loop []
      (let [session (<! in)
            ops (-> (l/ops session) (l/diff-ops))
            next-session (l/replace-listener session)]
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
      (let [ops (<! in)]
       (apply-removals-to-view-model! (mapv util/record->vec (:removed ops)))
       (>! out ops)
       (recur)))
   out))

(defn apply-additions-to-store
  "Reads ops from channel and applies additions to store"
  [in]
  (go-loop []
    (let [ops (<! in)]
      (apply-additions-to-view-model! (mapv util/record->vec (:added ops)))
      (>! done-ch :done)
      (recur)))
  nil)

;; Create session/store update pipeline
(transactor)

(def realized-session (apply-changes-to-session session->store))

(def changes-out (read-changes-from-session realized-session))

(def changes-mult (async/mult changes-out))

(defn changes-xf [f]
  (fn [rf]
    (fn
      ([] (rf))
      ([result] (rf result))
      ([result input]
       (let [ret {:added (f (:added input))
                  :removed (f (:removed input))}]
         (rf result ret))))))

(defn create-change-report-ch
  "Returns core.async channel with operational changes from session.
  Removes Precept implementation facts when called with no arguments.
  May be called with a function that will be applied to all :added and :removed facts.

  Usage:
  ```clj
  (def nil-values-ch
    (create-changes-report-ch
      (filter (fn [{:keys [e a v t]} record] (nil? v))
              %)))
  (go-loop []
    (let [changes (<! nil-values-ch)]
      (println \"Facts with nil values added / removed:\" changes)))
  => Facts with nil values added / removed: {:added () :removed ()}
  ```"
  ([] (create-change-report-ch util/remove-impl-attrs))
  ([f] (async/tap changes-mult (chan 1 (changes-xf f)))))

(def removals-out (apply-removals-to-store (create-change-report-ch)))

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
    (swap! s/state assoc-in [:subscriptions id] {:id id :name name :lens lens})
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
    (do
      (swap-session-sync!
        (l/replace-listener (:session opts))
        #(dispatch! (fn [session] (util/insert session (:facts opts)))))
      (swap! s/state assoc :started? true))))

(defn resume!
  "Resets session with provided facts if `start!` has been called, otherwise returns the session
  received as an argument unmodified. Avoids duplicate session creation on page refresh in
  development when there is stale session metadata in the compiler."
  [{:keys [session facts] :as options}]
  (let [opts (or options (hash-map))]
    (if (:started? @s/state)
      (swap-session-sync!
        (l/replace-listener (:session opts))
        #(dispatch! (fn [session] (util/insert session (:facts opts)))))
      session)))
