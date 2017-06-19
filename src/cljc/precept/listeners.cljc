(ns precept.listeners
  (:require [clara.rules.engine :as eng]
            [precept.util :as util]
            [clara.rules.listener :as l]))

(declare append-trace)
(declare to-transient-fact-listener)

(deftype PersistentFactListener [trace]
  l/IPersistentEventListener
  (to-transient [listener]
    (to-transient-fact-listener listener)))

(deftype TransientFactListener [trace]
  l/ITransientEventListener
  (insert-facts! [listener facts]
    (append-trace listener {:type :add-facts :facts facts}))

  (insert-facts-logical! [listener node token facts]
    (append-trace listener {:type :add-facts-logical :facts facts}))

  (retract-facts! [listener facts]
    (append-trace listener {:type :retract-facts :facts facts}))

  (retract-facts-logical! [listener node token facts]
    (append-trace listener {:type :retract-facts-logical :facts facts}))

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

; Copied from clara.tools.tracing
(defn to-transient-fact-listener [listener]
  (TransientFactListener. (atom (.-trace listener))))

; Copied from Clara and modified
(defn append-trace
  "Appends a trace event and returns a new listener with it."
  [^TransientFactListener listener event]
  (reset! (.-trace listener) (conj @(.-trace listener) event)))

; Copied from Clara and modified
(defn add-listener
  "Wraps session wrapped with a fact listener or the provided listener if given"
  ([session]
   (let [{:keys [listeners] :as components} (eng/components session)]
     (eng/assemble
       (assoc components
         :listeners
         (conj listeners (PersistentFactListener. []))))))
  ([session listener]
   (let [{:keys [listeners] :as components} (eng/components session)]
     (eng/assemble
       (assoc components
         :listeners
         (conj listeners listener))))))

(defn remove-fact-listeners
  "Returns a new session identical to the given one, but with tracing disabled
   The given session is returned unmodified if tracing is already disabled."
  [session]
  (let [{:keys [listeners] :as components} (eng/components session)]
    (eng/assemble
      (assoc components
        :listeners
        (remove #(instance? PersistentFactListener %) listeners)))))

(defn all-listeners
  "Returns all listener instances or empty list if none."
  [session]
  (:listeners (eng/components session)))

; Copied from Clara and modified
(defn fact-traces
  "Returns [[]...]. List of fact events for each fact listener in the session."
  [session]
  (if-let [listeners (all-listeners session)]
    (mapv #(.-trace ^PersistentFactListener %) listeners)))

(defn trace-by-type [trace]
  (select-keys
    (group-by :type trace)
    [:add-facts :add-facts-logical :retract-facts :retract-facts-logical]))

(defn retractions [trace-by-type]
  (select-keys trace-by-type [:retract-facts :retract-facts-logical #_:accum-reduced]))

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
  "Returns :added, :removed results for a single fact listener. Usually wrapped with `embed-ops`."
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

  ;(split-ops (vectorize-trace (first (fact-traces session)))))

(defn with-op [change op-kw]
  (mapv (fn [ent] (conj ent (vector (ffirst ent) :op op-kw)))
    (partition-by first change)))

(defn embed-op [additions-or-removals op-kw]
  (mapv util/tuple-entity->hash-map-entity
      (with-op additions-or-removals op-kw)))

(defn replace-listener [session]
  (-> session
    (remove-fact-listeners)
    (add-listener)))

(defn change->attr [change]
  (first (remove #{:db/id :op} (keys change))))

(defn change->av-map
  "Removes :op, :db/id from change for an entity"
  [change]
  (into {} (remove #(#{:op :db/id} (first %))
             change)))
