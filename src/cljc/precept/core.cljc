(ns precept.core
    (:require [precept.util :as util]
              [precept.listeners :as l]
              [precept.state :refer [fact-id rules store state] :as s]
              [clara.rules :refer [fire-rules]]
              [precept.serialize.facts :as serialize]
              [precept.orm :as orm]
              [precept.spec.core :refer [validate]]
              [precept.spec.sub :as sub]
              [precept.spec.lang :as lang]
              [taoensso.sente :as sente]
      #?(:clj
              [clojure.core.async :refer [<! >! put! take! chan go go-loop] :as async])
      #?(:cljs [cljs.core.async :refer [put! take! chan <! >!] :as async])
      #?(:cljs [reagent.core :as r])
        [precept.state :as state])
  #?(:cljs (:require-macros [cljs.core.async.macros :refer [go go-loop]])))

#?(:cljs (enable-console-print!))

(defn trace [& args]
  (comment (println args)))

(def action-ch (chan 1))
(def session->store (chan 1))
(def done-ch (chan 1))
(def init-session-ch (chan 1))

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

(defmethod register-rule "define" [{:keys [_ ns type lhs rhs source]}]
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
                 :source source
                 :ns ns
                 :lhs lhs
                 :rhs rhs}]
      (swap! rules assoc name entry)
      (symbol (:name entry)))))

(defmethod register-rule :default [{:keys [name ns type lhs rhs source]}]
  (if-let [existing (get rules name)]
    ;(println (str "Found " type " with same conditions and consequences as existing definition: "
    (:name existing)
    (let [id (util/guid)
          entry {:id id
                 :type type
                 :name name
                 :ns ns
                 :source source
                 :lhs lhs
                 :rhs rhs}]
      (swap! rules assoc name entry)
      (:name entry))))

(defn notify! [sub-name update-fn]
  (let [sub-id (:id (util/find-sub-by-name sub-name))]
    (swap! store update-in [sub-id ::sub/response] update-fn)))

(defn update-session-history
  "First history entry is most recent"
  [session]
  (if (= 5 (count (:session-history @s/state)))
    (swap! s/state update :session-history
      (fn [sessions] (conj (butlast sessions) session)))
    (swap! s/state update :session-history conj session)))

(defn swap-session!
  [next]
  (swap! s/state assoc :session next))

(defn swap-session-sync! [next f]
  (swap! s/state assoc :session next)
  (put! init-session-ch true)
  (f)
  (:session @s/state))

(defn dispatch! [f] (put! action-ch f))

(defn transactor
  "Takes from action channel and puts to `session->store` channel. Parks until `done-ch`
  returns a message indicating:
    1. A transaction of facts (via `then`)  was inserted into the (global) session
       (`(:session precept.state/state)`)
    2. `fire-rules` was called on `(:session precept.state/state)`
    3. changes were read from the session and applied to the store"
  []
  (go-loop []
   (let [action (<! action-ch)]
        (do (>! session->store action)
            (<! done-ch)
            (recur)))))

(defn init-transactor
  "Parks until session is initialized in (:session precept.state/state) then spawns
  a transactor that takes from `action-ch`."
  []
  (go (<! init-session-ch)
      (transactor)))

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
            next-session (if (:connected? @s/*devtools)
                           (do
                             (let [total-events (dec (:event-number @s/*event-coords))]
                               (>! (:event-sink @s/*devtools)
                                 {:fire-rules-complete true
                                  :total-events total-events}))
                             (swap! s/*event-coords update :state-number inc)
                             (swap! s/*event-coords assoc :state-id (util/guid)
                                                          :event-number 0)
                             (l/replace-listener
                               session
                               (l/create-devtools-listeners
                                 (:event-sink @s/*devtools)
                                 s/*event-coords
                                 [])))
                           (l/replace-listener session))]
        (swap-session! next-session)
        (>! out ops)
        (recur)))
    out))

(defn apply-removals-to-store
  "Reads ops from in channel and applies removals to store"
  [in]
  (let [out (chan)]
    (go-loop []
      (let [ops (<! in)]
       (orm/update-tree! s/store @s/ancestors-fn {:remove (mapv util/record->vec (:removed ops))})
       (>! out ops)
       (recur)))
   out))

(defn apply-additions-to-store
  "Reads ops from channel and applies additions to store"
  [in]
  (go-loop []
    (let [ops (<! in)]
      (orm/update-tree! s/store @s/ancestors-fn {:add (mapv util/record->vec (:added ops))})
      (>! done-ch :done)
      (recur)))
  nil)

;; Create session/store update pipeline
(init-transactor)

(def realized-session (apply-changes-to-session session->store))

(def session-mult (async/mult realized-session))

(defn create-fired-session-ch
  "Returns core.async channel that receives the session after the rules have been fired and
  before its listener has been replaced."
  []
  (async/tap session-mult (chan)))

(defn batch-complete?
  "Returns true if the event is fire rules complete or it exists in the batch and
  the maximum event number received is equal to `:total-events` from fire rules complete"
  [event batch]
  (let [max-event-number-recd (max (:event-number event)
                                   (apply max (map :event-number batch)))
        fire-rules-complete-event (or (first (filter :fire-rules-complete batch))
                                      (when (some-> :fire-rules-complete event)
                                        event))
        total-events (:total-events fire-rules-complete-event)]
    (and (integer? total-events)
         (integer? max-event-number-recd)
         (= total-events max-event-number-recd))))

(defn empty-batch?
  [event batch]
  (and (= event {:fire-rules-complete true :total-events -1})
       (empty? batch)))

(defn create-dto>socket-router
  "Returns a go-loop that takes from a channel with events emitted by
  `precept.listeners/PersistentSessionEventMessager and calls the provided send function,
  intended for use with a precept-devtools socket. Batches events per call to `fire-rules`."
  [in send! encoding]
  (go-loop [batch []]
    (let [event (<! in)]
      (cond
        ;; A state might only be comprised of events that were ignored.
        ;; However, we still want to send it to ensure orm-state numbers line up.
        (empty-batch? event batch)
        (do (send! [:devtools/update
                    {:encoding encoding
                     :payload (serialize/serialize encoding batch)}])
            (recur []))

        (batch-complete? event batch)
        (do (send! [:devtools/update
                    {:encoding encoding
                     :payload (serialize/serialize encoding batch)}])
            (recur []))

        :default
        (recur (conj batch event))))))

(def changes-out (read-changes-from-session (create-fired-session-ch)))

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
  ([] (create-change-report-ch util/remove-rulegen-facts))
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
         existing (util/find-sub-by-name name)]
     (or (:lens existing) (register req)))))

(defn then
  "Inserts facts into current session"
  [facts]
  (dispatch! (fn [session] (util/insert session facts))))

(def default-devtools-options
  {:host "0.0.0.0:3232"
   :path "/chsk"
   :encoding :json-verbose})

(defn connect-devtools-socket!
  [options init-cb]
  (let [{:keys [chsk ch-recv send-fn state]} (sente/make-channel-socket!
                                               (:path options)
                                               {:host (:host options)
                                                :type :auto})]
    (def chsk       chsk)
    (def ch-chsk    ch-recv)
    (def chsk-send! send-fn)
    (def chsk-state state)
    (def devtools-socket-ch (chan 5000))
    (def devtools-socket-router (create-dto>socket-router
                                  devtools-socket-ch send-fn (:encoding options)))

    (swap! s/*devtools assoc
      :event-sink devtools-socket-ch
      :host (:host options)
      :path (:path options)
      :encoding (:encoding options))

    (defmulti handle-message first)
    (defmethod handle-message :chsk/ws-ping [_])

    (defmethod handle-message :devtools/init [[msg-name msg]]
      (println "[precept-devtools] Server sent: " msg))

    (defmulti handle-event :id)
    (defmethod handle-event :chsk/handshake [_])

    (defmethod handle-event :chsk/state [{:keys [?data]}]
      (let [[last-state this-state] ?data]
        (when (:first-open? this-state)
          (do (println (str "[precept-devtools] Connected to  "
                            (:host @s/*devtools) (:path @s/*devtools) "."))
              (swap! s/*devtools assoc :connected? true)
              (init-cb (:event-sink @s/*devtools) send-fn)))))

    (defmethod handle-event :chsk/recv [{:keys [?data]}]
      (handle-message ?data))

    (sente/start-chsk-router! ch-recv handle-event)))

(defn start-with-devtools!
  [{:keys [session facts devtools] :as options}]
  (let [devtools-options (if (true? devtools)
                           default-devtools-options
                           (merge default-devtools-options devtools))]
    (connect-devtools-socket! devtools-options
      (fn [ch send-fn]
        (let [schemas (into {} (filter (comp some? second) @state/schemas))
              rules (into [] (vals @state/rules))]
          (when (not-empty schemas)
            (send-fn [:devtools/schemas
                      {:encoding (:encoding devtools-options)
                       :payload (serialize/serialize
                                 (:encoding devtools-options)
                                 schemas)}]))
          (when (not-empty rules)
            (send-fn [:devtools/rule-definitions
                      {:encoding (:encoding devtools-options)
                       :payload (serialize/serialize
                                  (:encoding devtools-options)
                                  rules)}])))
        (do
          (swap-session-sync!
            (l/replace-listener
              (:session options)
              (l/create-devtools-listeners ch s/*event-coords []))
            #(dispatch! (fn [session] (util/insert session (:facts options)))))
          (swap! s/state assoc :started? true))))))

;; TODO. Allow custom encoding fn for devtools/socket
;; TODO. Allow keywordize keys option (default true)
(defn start!
  "Initializes session with facts.

  - `:session` - Instance of clara.rules.engine.LocalSession created by `precept.rules/session`
  - `:id` - Value that uniquely identifies the session.
  - `:facts` - Vector. Initial facts to be inserted into the session
  - `:devtools` (optional) - Boolean or map of options for connecting to a
                             running instance of a Precept devtools server. Default: `nil`.
    Supported devtools options:
    - `:host` - String with host and port separated by `:`.
                Defaults to default Devtools server address and port `0.0.0.0:3232`.
    - `:path` - String of path for server socket.
                Defaults to default Devtools server path `/chsk`.
    - `:encoding` - Keyword of encoding for devtools socket.
                    One of transit enc-types: `:json`, `:json-verbose` . Defaults to
                    `:json-verbose`.
  "
  [{:keys [id session facts devtools] :as options}]
  (if devtools
    (start-with-devtools! options)
    (do
      (swap-session-sync!
        (l/replace-listener (:session options))
        #(dispatch! (fn [session] (util/insert session (:facts options)))))
      (swap! s/state assoc :started? true))))

(defn resume-with-devtools!
  [options]
  (swap-session-sync!
    (l/replace-listener (:session options)
      (l/create-devtools-listeners (:event-sink @s/*devtools) s/*event-coords []))
    #(dispatch! (fn [session] (util/insert session (:facts options))))))


(defn resume!
  "Resets session with provided facts if `start!` has been called, otherwise returns the session
  received as an argument unmodified. Avoids duplicate session creation on page refresh in
  development when there is stale session metadata in the compiler."
  [{:keys [session facts devtools] :as options}]
  (if (not (:started? @s/state))
    session
    (if (:connected? @s/*devtools)
      (resume-with-devtools! options)
      (swap-session-sync!
        (l/replace-listener (:session options))
        #(dispatch! (fn [session] (util/insert session (:facts options))))))))
