(ns precept.listeners
  (:require [clara.rules.engine :as eng]
            [precept.util :as util]
            [clara.rules.listener :as l]
            [clojure.core.async :as async]))

(declare append-trace)
(declare to-transient-fact-listener)
(declare to-transient-session-event-messenger)

(deftype PersistentFactListener [trace]
  l/IPersistentEventListener
  (to-transient [listener]
    (to-transient-fact-listener listener)))

(deftype PersistentSessionEventMessenger [ch *event-coords]
  l/IPersistentEventListener
  (to-transient [listener]
    (to-transient-session-event-messenger listener)))

(defn define-name? [name lhs]
  ;;TODO. This isn't good enough--user-generated rule names could include "define-".
  ;; Attempted to recreate the name from the LHS arg, but the encoding is different
  ;; than what we use to generate the hash for the name since it comes from clara
  ;(= name (str "define-" (hash lhs))))
  (clojure.string/includes? name "define-"))

(defn name-or-lhs-str [name lhs]
  (if (define-name? name lhs) (str lhs) name))

(defn without-namespace [s]
  (second (clojure.string/split s #"/")))

(defn get-rule-display-name [name lhs]
  (-> (without-namespace name)
    (name-or-lhs-str lhs)))

(defn get-fact-match-or-matches
  [matches]
  (let [x (map first matches)]
    (if (= 1 (count x)) (first x) x)))

(defn untracked-impl-rule?
  [ns-name name]
  (and (= 'precept.impl.rules ns-name)
       (not= name "clean-transients___impl")))

(defn sub-registration? [facts]
  (and (= (count facts) 1)
       (= (:a (first facts))
          :precept.spec.sub/request)))

(defmulti rule-event-dto :encoding)

(defmethod rule-event-dto :json
  [{:keys [type event-number state-number state-id display-name name ns-name lhs rhs props
           matches bindings facts]}]
  {"type" type
   "eventNumber" event-number
   "stateNumber" state-number
   "stateId" state-id
   "displayName" display-name
   "name" name
   "nsName" ns-name
   "lhs" lhs
   "rhs" rhs
   "props" props
   "matches" matches
   "bindings" bindings
   "facts" facts})

(defmethod rule-event-dto :default [m]
  (dissoc m :encoding))

(defmulti action-dto :encoding)

(defmethod action-dto :json [{:keys [type action? facts event-number state-number state-id]}]
  {"type" type
   "action" action?
   "facts" facts
   "eventNumber" event-number
   "stateNumber" state-number
   "stateId" state-id})

(defmethod action-dto :default [m]
  (dissoc m :encoding))

;; TODO. Allow configure encoding for "JSON" vs. "EDN" (string keys vs. keyword keys)
(defn event-dto
  "Data transfer object.
  - `event` - keyword. One of `#{:add-facts :add-facts-logical :retract-facts
                                 :retract-facts-logical}`
  - node - hash-map (nilable).
  - token - hash-map (nilable).
  - facts - vector (nilable)."
  ([event node token facts *event-coords]
   (event-dto event node token facts *event-coords :edn))
  ([event node token facts *event-coords encoding]
   (let [{:keys [event-number state-number state-id]} @*event-coords]
     (cond
       (sub-registration? facts)
       {:impl? true}

       (and (= nil node token) (= event-number 0))
       (action-dto {:type event
                    :action true
                    :facts (util/record->map facts)
                    :event-number event-number
                    :state-number state-number
                    :state-id state-id
                    :encoding encoding})

       ;; TODO. May not be getting node, token from :retract-facts
       (and (= nil node token))
       (action-dto {:type event
                    :action true
                    :facts (util/record->map facts)
                    :event-number event-number
                    :state-number state-number
                    :state-id state-id
                    :encoding encoding})
       :default
       (let [rule (:production node)
             {:keys [ns-name lhs rhs props name]} rule
             {:keys [matches bindings]} token
             display-name (get-rule-display-name name lhs)]
         (if (untracked-impl-rule? ns-name display-name)
           {:impl? true}
           (let [{:keys [event-number state-number state-id]} @*event-coords]
             (rule-event-dto {:type event
                              :event-number event-number
                              :state-number state-number
                              :state-id state-id
                              :display-name display-name
                              :name name
                              :ns-name ns-name
                              :lhs lhs
                              :rhs rhs
                              :props props
                              :matches (util/record->map matches)
                              :bindings (util/record->map bindings)
                              :facts (util/record->map facts)
                              :encoding encoding}))))))))

(defn deconstruct-node-token
  [node token]
  (if (= nil node token)
    {:action true}
    (let [rule (:production node)
          {:keys [ns-name lhs rhs props name]} rule
          {:keys [matches bindings]} token
          display-name (get-rule-display-name name lhs)]
      {:rule rule
       :name display-name
       :ns-name ns-name
       :lhs lhs
       :rhs rhs
       :props props
       :matches matches
       :bindings bindings})))

(defn explain-insert-facts
  [node token facts]
  (let [{:keys [rule name ns-name lhs rhs props matches bindings action]}
        (deconstruct-node-token node token)]
    (if action
      (println "Facts inserted unconditionally from outside session: " facts)
      (let [{:keys [matches bindings]} token
            matched-facts (get-fact-match-or-matches matches)]
        (println "Rule ")
        (println name)
        (println " executed ")
        (println rhs)
        (println "because facts ")
        (println matched-facts)
        (println "matched the conditions")
        (doseq [condition lhs]
          (println "  - Fact type: " (:type condition))
          (println "  - Constraints: " (:constraints condition)))))))

(defn check-retract-facts-logical-failure
  [facts]
  (doseq [fact facts]
     (when-let [failed-to-remove? (some false? (util/remove-fact-from-index! fact))]
       (throw (ex-info "Failed to remove logical retraction. This is not expected behavior and
       may result in unexpected schema-based truth maintenence. If you continue to see
       this error, please file an issue at https://github.com/CoNarrative/precept/issues."
                {:fact fact})))))

(defn split-matches-and-tokens
  "Splits matches into vector of facts, tokens"
  [matches]
  (reduce (fn [[facts tokens] [fact token]]
            [(conj facts fact) (conj tokens token)])
        [[][]]
        matches))

(defn handle-event!
  [ch *event-coords type node token facts]
  (let [dto (event-dto type node token facts *event-coords)
        pred #(not (:impl? %))]
    (when (pred dto)
      (do (async/put! ch dto)
          (swap! *event-coords update :event-number inc)))))

(deftype TransientSessionEventMessenger [ch *event-coords]
  l/ITransientEventListener
  (insert-facts! [listener node token facts]
    (handle-event! ch *event-coords :add-facts node token facts))

  (insert-facts-logical! [listener node token facts]
    (handle-event! ch *event-coords :add-facts-logical node token facts))

  (retract-facts! [listener node token facts]
    (handle-event! ch *event-coords :retract-facts node token facts))

  (retract-facts-logical! [listener node token facts]
    (do
      (handle-event! ch *event-coords :retract-facts-logical node token facts)))
      ;; TODO. Decide whether errors should be put on the channel. If so, be aware
      ;; FactListener is currently throwing this error
      ;(check-retract-facts-logical-failure facts)

  (to-persistent! [listener]
    (PersistentSessionEventMessenger. ch *event-coords))
  ;; no-ops
  (alpha-activate! [listener node facts])
  (alpha-retract! [listener node facts])
  (left-activate! [listener node tokens])
  (left-retract! [listener node tokens])
  (right-activate! [listener node elements])
  (right-retract! [listener node elements])
  (add-activations! [listener node activations])
  (remove-activations! [listener node activations])
  (add-accum-reduced! [listener node join-bindings result fact-bindings])
  (remove-accum-reduced! [listener node join-bindings fact-bindings]))

(deftype TransientFactListener [trace]
  l/ITransientEventListener
  (insert-facts! [listener node token facts]
    (append-trace listener {:type :add-facts :facts facts}))

  (insert-facts-logical! [listener node token facts]
    (append-trace listener {:type :add-facts-logical :facts facts}))

  (retract-facts! [listener node token facts]
    (append-trace listener {:type :retract-facts :facts facts}))

  (retract-facts-logical! [listener node token facts]
    (do
      (check-retract-facts-logical-failure facts)
      (append-trace listener {:type :retract-facts-logical :facts facts})))

  (to-persistent! [listener]
    (PersistentFactListener. @trace))
  ;; no-ops
  (alpha-activate! [listener node facts])
  (alpha-retract! [listener node facts])
  (left-activate! [listener node tokens])
  (left-retract! [listener node tokens])
  (right-activate! [listener node elements])
  (right-retract! [listener node elements])
  (add-activations! [listener node activations])
  (remove-activations! [listener node activations])
  (add-accum-reduced! [listener node join-bindings result fact-bindings])
  (remove-accum-reduced! [listener node join-bindings fact-bindings]))

(defn to-transient-session-event-messenger [listener]
  [listener]
  (TransientSessionEventMessenger.
    (.-ch listener)
    (.-*event-coords listener)))

; Copied from clara.tools.tracing
(defn to-transient-fact-listener
  [listener]
  (TransientFactListener. (atom (.-trace listener))))

; Copied from Clara and modified
(defn append-trace
  "Appends a trace event and returns a new listener with it."
  [^TransientFactListener listener event]
  (reset! (.-trace listener) (conj @(.-trace listener) event)))

(defn all-listeners
  "Returns all listener instances or empty list if none."
  [session]
  (:listeners (eng/components session)))

; Copied from Clara and modified
(defn fact-traces
  "Returns [[]...]. List of fact events for each fact listener in the session."
  [session]
  (if-let [listeners (all-listeners session)]
    (->> listeners
       (mapcat
        (fn [listener]
          (cond
            (instance? clara.rules.listener.PersistentDelegatingListener listener)
            (->> (.-children listener)
              (filter #(instance? PersistentFactListener %))
              (mapv #(.-trace %)))
            (instance? PersistentFactListener listener)
            (vector (.-trace listener)))))
       (filter some?))))

(defn trace-by-type [trace]
  (select-keys
    (group-by :type trace)
    [:add-facts :add-facts-logical :retract-facts :retract-facts-logical]))

(defn retractions [trace-by-type]
  (select-keys trace-by-type [:retract-facts :retract-facts-logical]))

(defn insertions [trace-by-type]
  (select-keys trace-by-type [:add-facts :add-facts-logical]))

(defn list-facts [xs]
  (mapcat :facts (mapcat identity (vals xs))))

(defn split-ops
  "Takes trace returned by Clara's get-trace. Returns m of :added, :removed"
  [trace]
  (let [by-type (trace-by-type trace)
        added (list-facts (insertions by-type))
        removed (list-facts (retractions by-type))]
    {:added (into [] added)
     :removed (into [] removed)}))

(defn diff-ops
  "Returns net result of session changes in order to eliminate ordinal significance of add/remove
  mutations to view-model"
  [ops]
  (let [added (clojure.set/difference (set (:added ops))
                                      (set (:removed ops)))
        removed (clojure.set/difference (set (:removed ops))
                                        (set (:added ops)))]
    {:added added
     :removed removed}))

(defn ops
  "Returns :added, :removed results. Assumes a single fact listener in the vector of
   session's `:listeners` that may be a child of a PersistentDelegatingListener."
  [session]
  (split-ops (first (fact-traces session))))

(defn vectorize-trace [trace]
  (mapv #(update % :facts
          (fn [facts]
            (map util/record->vec facts)))
     trace))

(defn vec-ops
  "Takes a session with a FactListener and returns the result of the trace
  as {:added [vector tuples] :removed [vector tuples]}"
  [session]
  (let [diff (-> (ops session) (diff-ops))]
    {:added (mapv util/record->vec (:added diff))
     :removed (mapv util/record->vec (:removed diff))}))

(defn create-fact-listener
  ([] (PersistentFactListener. []))
  ([initial-trace] (PersistentFactListener. initial-trace)))

(defn replace-listener
  "Removes and adds listener(s) from session.
  When called with `session` only adds PersistentFactListener with initial state of []."
  ([session]
   (let [{:keys [listeners] :as components} (eng/components session)]
     (eng/assemble (assoc components :listeners (vector (create-fact-listener))))))
  ([session listener]
   (let [{:keys [listeners] :as components} (eng/components session)]
     (eng/assemble (assoc components :listeners (vector listener))))))

(defn create-devtools-listeners
  "Returns Clara DelegatingListener that sends listener events through a PersistentFactListener
  and PersistentSessionEventMessanger constructed with the supplied arguments.
  - `initial-trace` - vector or nil. Passed to fact listener. Defaults to [].
  - ``ch` - core.async channel that PersistentSessionEventMessenger will put events to
  ` `*event-coords` - atom with keys `:event-number`, `:state-number`, `:state-id`.
                     `:event-number` updated within SessionEventMessenger methods."
  [ch *event-coords initial-trace]
  (l/delegating-listener
    [(PersistentFactListener. (or initial-trace []))
     (PersistentSessionEventMessenger. ch *event-coords)]))
