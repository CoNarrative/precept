(ns ^:figwheel-always libx.core
    (:refer-clojure :exclude [send])
    (:require [libx.util :refer [entity-tuples->entity-map] :as util]
              [libx.listeners :refer [ops] :as l]
              [libx.schema :as schema]
              [clara.rules :refer [query fire-rules insert! insert-all!] :as cr]
              [clara.rules.accumulators :as acc]
              [libx.lang :as lang]
              [libx.tuplerules :refer [def-tuple-session def-tuple-rule def-tuple-query]]
              [clojure.spec :as s]
      #?(:clj [clojure.core.async :refer [<! >! put! take! chan go go-loop]])
      #?(:clj [reagent.ratom :as rr])
      #?(:cljs [cljs.core.async :refer [put! take! chan <! >!]])
      #?(:cljs [libx.todomvc.schema :refer [app-schema]])
      #?(:cljs [libx.todomvc.rules :refer [find-all-facts]])
      #?(:cljs [reagent.core :as r]))
  #?(:cljs (:require-macros [cljs.core.async.macros :refer [go go-loop]])))

;#(:cljs (enable-console-print!))
; TODO. keep vector of states if dev
;(def dev? true)

(defn mk-ratom [args]
  #?(:clj (atom args) :cljs (r/atom args)))

(defonce store (mk-ratom {}))

(defonce state (atom {:subscriptions {}
                      :session nil
                      :schema nil}))

(def changes-ch (chan))

(def session-ch (chan))

(defn put-session!
  "Returns session"
  [session]
  (put! session-ch session)
  session)

(defn swap-session!
  "Mainly created for debugging"
  [next]
  ;(println "Updating session atom")
  (swap! state update :session (fn [old] next)))

(defn lens [a path]
  #?(:clj (atom (get-in @a path)) ;; for testing in clj only
     :cljs (r/cursor a path)))

(defn register
  "Returns existing subscription if found in subscriptions. Otherwise returns"
  ([path]
   (if-let [existing (get (:subscriptions @state) path)]
      (do (println "Found existing subscription" existing) existing)
      (let [inserted-sub (swap! state update :session
                           (fn [old] (-> old (util/insert [(util/guid) :sub path]))))
            lens (lens store (vector path))]
        (println "Registering new" path)
        (go (>! session-ch (:session inserted-sub)))
        (swap! state update-in (into [:subscriptions] (vector path)) (fn [_] lens))
        lens))))


;TODO. Return vector of maps if multiple paths provided, otherwise single map
; Verify cursors update on change to @state and that change propagates to component
(defn subscribe
  "Returns ratom of reagent cursors, one for each path in paths.
   * paths - vector containing sub requests, any of which may contain parameters.
             Examples: `[:kw]` `[:kw \"someval\"]` `[[:kw \"someval\"] [:kw2]]`
  Returned ratom will contain a single map for single subscription or vector of maps for multiple"
  ([paths]
   (let [existing-subs (select-keys (:subscriptions @state) paths)
         new-paths (remove (set (keys existing-subs)) paths)
         requested-subs (reduce
                          (fn [acc path]
                            (let [vector-path (if (vector? path) path (vector path))
                                  _ (println "Rec'd register req for" vector-path)
                                  register-result (register vector-path)
                                  _ (println "Register result" register-result)]
                              (conj acc register-result)))
                          (or (vals existing-subs) [])
                          new-paths)
         _ (println "Requested subs" requested-subs)]
     (if (second requested-subs)
       requested-subs
       (first requested-subs)))))

      ;(mk-ratom (if (second requested-subs)
      ;              requested-subs
      ;              (first requested-subs)))))))

(defn with-op [change op-kw]
  (mapv (fn [ent] (conj ent (vector (ffirst ent) :op op-kw)))
    (partition-by first change)))

(defn embed-op [changes]
  (let [added (:added changes)
        removed (:removed changes)]
    (mapv entity-tuples->entity-map
      (into (with-op added :add)
        (with-op removed :remove)))))

(defn clean-changes [changes]
  "Removes :op, :db/id from change for an entity"
  (remove #(#{:op :db/id} (first %)) changes))

(defn add [a changes]
  "Merges changes into atom"
  (println "Adding" changes)
  (swap! a merge (dissoc changes :op :db/id)))

(defn del [a changes]
  "Removes keys in change from atom"
  (swap! a (fn [m] (apply dissoc m (keys (clean-changes changes))))))

(defn create-change->store-router! [in-ch]
  "Reads changes from in channel and updates store
   * `in-ch` - core.async channel
   * `store` - atom"
  (go-loop []
    (let [change (<! in-ch)
          id (:db/id change)
          op (:op change)
          _ (println "Change id op atom" change id op)]
      (condp = op
        :add (do (add store change) (recur))
        :remove (do (del store change) (recur))
        (do (println "No match for" change) (recur))))))

;(defn create-session-ch!
;  ([] (binding [session-ch session-ch]
;        (set! session-ch (chan 1)))
;  ([ch] (set! session-ch ch)))
;
;(defn create-changes-ch! [ch]
;  ([] (binding [changes-ch changes-ch]
;        (set! changes-ch (chan 1)))
;  ([ch] (set! changes-ch ch)))

(defn query? [x] (and (seq x) (= :where (first x))))

(defn parse-query-params [exprs]
  (println "Parsing query params" exprs)
  (mapcat
    (fn [expr]
       (println "Expr" expr (s/valid? ::lang/variable-binding (second expr)))
       (filter #(s/valid? ::lang/variable-binding %) expr))
    exprs))

(defn run-query [exprs]
  (println "Run query")
  (let [params (parse-query-params exprs)]
    (println "params")))
    ;(clara.rules/query @session-atom
    ;  (clara.rules.dsl/parse-query params exprs)))) ;TODO. Used in clara test but passed
    ; directly to mk-session

(defn send-with-query [[op exprs]]
  (println "Send with query" op exprs)
  (let [query (second exprs)
        query-result (run-query query)]
    (condp = op
      :remove (swap! state update :session (fn [old] (-> old (util/retract query-result))))
      :replace  (swap! state update :session
                  (fn [old]
                   (-> old
                     (util/retract query-result)
                     (util/insert (second double))))))))


;; 1. Decide whether we require a schema in defsession
;; If we do...
;; 2. Check schema is not nil in state
;; 3. For each attr in facts
;;    * if unique,
;;        remove all facts with unique attr and insert new fact
;;        else insert new fact
(defn schema-insert
  ([facts]
   (let [schema (:schema @state)
         facts-v (if (coll? (first facts)) facts (vector facts))
         _ (println "factsvvv" facts-v)
         tuples (mapcat util/insertable facts-v)
         unique-attrs (reduce (fn [acc cur]
                                (if (schema/unique-attr? (:schema @state) (second cur))
                                    (conj acc (second cur))
                                    acc))
                         [] tuples)
         existing-unique-facts (mapcat #(util/facts-where (:session @state) %) unique-attrs)
         next-session (-> (:session @state)
                         (l/replace-listener)
                         (util/retract existing-unique-facts)
                         (cr/insert-all tuples))]
        (put-session! next-session)))
  ([session facts] (schema-insert facts)))

(defn then
  ([op facts]
   (condp = op
     :add (schema-insert facts)
     (println "Unsupported op keyword " op)))
  ([facts] (then :add facts)))

(defn send [& exprs]
  (let [msgs (reduce
                  (fn [acc cur]
                     (if (query? (second cur))
                       (conj acc [:__query (vector (first cur) (second (second cur)))])
                       acc))
                 [] (partition 2 exprs))]
    (for [msg msgs]
      (condp (first msg)
        :__query (send-with-query (second msg))
        :add (swap! state update :session (fn [old] (-> old (util/insert (second msg)))))
        :remove (swap! state update :session (fn [old] (-> old (util/retract (second msg)))))
        :replace  (swap! state update :session
                    (fn [old] (-> old
                                (util/retract (second msg))
                                (util/insert (second msg)))))))))

(defn create-session->change-router!
  "Reads changes from session channel, fires rules, and puts resultant changes
  on changes channel. Updates session state atom with new session."
  [in-ch out-ch]
  (go-loop []
    (let [session (<! in-ch)
          _ (println "Rec'd new session!" session)
          next (fire-rules session)
          changes (embed-op (ops next))
          _ (println "Session changes" changes)]
      (do
        (swap-session! (l/replace-listener next))
        (doseq [change changes]
          (put! out-ch change)))
      (recur))))

(defn init-schema [session schema]
  (swap! state update :schema (fn [_] (schema/by-ident schema)))
  (println "Updating schema with" schema)
  session)

(defn start! [options]
  (let [opts (or options (hash-map))
        init-session (swap-session! (:session opts))]
    (do
      (swap-session!
          (-> init-session
             (init-schema (:schema opts))
             (schema-insert (:facts opts))))
      (create-session->change-router! session-ch changes-ch)
      (create-change->store-router! changes-ch))))



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; test-area
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;(def-tuple-rule subs-footer-controls
;  [:exists [_ :sub [:footer]]]
;  [[_ :done-count ?done-count]]
;  [[_ :active-count ?active-count]]
;  [[_ :ui/visibility-filter ?visibility-filter]]
;  =>
;  (insert! [:lens [:footer] {:active-count ?active-count
;                             :done-count ?done-count
;                             :visibility-filter ?visibility-filter}]))
;
;(def-tuple-rule subs-task-list
;  [:exists [_ :sub [:task-list]]]
;  [?visible-todos <- (acc/all) :from [:todo/visible]]
;  [[_ :active-count ?active-count]]
;  =>
;  (insert-all! [[:lens [:task-list] {:visible-todos (libx.util/tuples->maps ?visible-todos)
;                                     :all-complete? (> ?active-count 0)}]]))
;(def-tuple-rule subs-todo-app
;  [:exists [:sub/todo-app]]
;  [?todos <- (acc/all) :from [:todo/title]]
;  =>
;  (println "All todos" ?todos)
;  (insert! [-1 :lens/todo-app (libx.util/tuples->maps (:todos ?todos))]))

;(def-tuple-query find-all-facts
;  []
;  [?facts <- (acc/all) :from [:all]])

;; Init
;(def session->change (create-session->change-router! session-ch changes-ch))
;(def change->store (create-change->store-router! changes-ch))

;; Reset
;(reset! store {})
;(swap! state update :subscriptions (fn [old] {}))
;(swap! state update :session (fn [old] nil))
;(init-schema app-schema)
;(def-tuple-session my-sess 'libx.core)
;(swap-session! (l/replace-listener my-sess))

;; Write
;(def facts [[-1 :active-count 7]
;            [-2 :done-count 1]
;            [-3 :todo/visible :tag]
;            [-4 :todo/title "Hi"]
;            [-5 :ui/visibility-filter :done]])
;(def next-session (schema-insert facts))
;(put-session! next-session)

;; Schema
;(init-schema app-schema)
;(schema-insert facts)

;; Read
;(:session @state)
;@store
;(:subscriptions @state)
;(:schema @state)

;;;;;;;;;;;;;;;;;
;; Problems
;; * Subs that are registered afterMUST be registered before any rules fire. Otherwise they won't
;; be notified of changes. Not clear right now how to best solve this. Rules should fire once subscription is
;; inserted.

;; TODO.
;; * Key store by sub's vector form (to allow params). Subscriptions atom already uses this format.
;; * Decide whether store only keeps "lens"-prefixed changes, or whether we should
;;   continue to maintain a record of the current state (regardless of whether there is a
;;   subscription for it)
;; * If a "lens" change is received, instead of putting it in the store, we could update the
;;   subscription directly. However, this would prevent shared subscriptions because we would not
;;   be maintaing a single source of truth accessible to multiple observers
;; * Lenses should return maps where they currently return tuples
