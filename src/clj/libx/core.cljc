(ns ^:figwheel-always libx.core
    (:refer-clojure :exclude [send])
    (:require [libx.util :refer [entity-tuples->entity-map] :as util]
              [libx.listeners :refer [ops] :as l]
              [libx.schema :as schema]
              [clara.rules :refer [query fire-rules insert! insert-all!] :as cr]
              [clara.rules.accumulators :as acc]
              [libx.spec.core :refer [validate]]
              [libx.spec.sub :as sub]
              [libx.spec.lang :as lang]
              [libx.tuplerules :refer [def-tuple-session def-tuple-rule def-tuple-query]]
      #?(:clj [clojure.core.async :refer [<! >! put! take! chan go go-loop]])
      #?(:clj [reagent.ratom :as rr])
      #?(:cljs [cljs.core.async :refer [put! take! chan <! >!]])
      #?(:cljs [libx.todomvc.rules :refer [find-all-facts]])
      #?(:cljs [reagent.core :as r]))
  #?(:cljs (:require-macros [cljs.core.async.macros :refer [go go-loop]])))

(defonce initial-state
  {:subscriptions {}
   :session nil
   :schema nil
   :session-history []})

(defonce state (atom initial-state))

(defn mk-ratom [args]
  #?(:clj (atom args) :cljs (r/atom args)))

(defonce store (mk-ratom {}))

(defn init-schema [schema]
  (swap! state assoc :schema (schema/by-ident schema)))

(def session-ch (chan 1))

;; changes, update-session, update-history
(defn advance-session!
  "Sole fn that should be used to advance session and state. Returns session"
  [session]
  (let [fired (fire-rules session)]
    (println "Advancing...")
    (go (>! session-ch fired))
    (swap! state assoc :session (l/replace-listener fired))
    (swap! state update :session-history (fn [prev] (conj prev fired)))))


(defn read-changes-from-session
  "Reads changes from session channel, fires rules, and puts resultant changes
  on changes channel. Updates session state atom with new session."
  [in]
  (let [out (chan)]
    (go-loop []
      (let [ops (l/ops (<! in))
            _ (println "Ops!" ops)]
        (>! out ops)
        (recur)))
    out))

(defn add [a change]
  "Merges change into atom"
  (println "Adding" (l/change->av-map change))
  (swap! a update (:db/id change) (fn [ent] (merge ent (l/change->av-map change)))))

(defn del [a change]
  "Removes keys in change from atom"
  (println "Removing" (l/change->attrs change))
  (swap! a update (:db/id change) dissoc (l/change->attrs change)))

(defn apply-removals-to-store [in]
  "Reads changes from in channel and updates store
   * `in-ch` - core.async channel
   * `store` - atom"
  (let [out (chan)]
    (go-loop []
      (let [changes (<! in)
            with-ops (l/embed-op {:removed changes})
            _ (println "Removals!" changes)]
       (doseq [change with-ops]
          (del store change))
       (>! out (:added changes))
       (recur)))
   out))

(defn apply-additions-to-store [in]
  "Reads changes from in channel and updates store
   * `in-ch` - core.async channel
   * `store` - atom"
  (go-loop []
    (let [changes (<! in)
          with-ops (l/embed-op {:added changes})
          _ (println "Additions!" (l/embed-op {:added changes}))]
      (doseq [change with-ops]
        (add store change))
      (recur)))
  nil)

(defn write-changes-to-store [in]
  "Reads changes from in channel and updates store
   * `in-ch` - core.async channel
   * `store` - atom"
  (go-loop []
    (let [change (<! in)
          op (:op change)]
      (condp = op
        :add (do (add store change) (recur))
        :remove (do (del store change) (recur))
        (do (println "No match for" change) (recur))))))
;; TODO. make go loop for removing changes and adding changes and pass through removals first
(def changes-out (read-changes-from-session session-ch))
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
    (mapcat (fn [[a v]] (util/facts-where (:session @state) a v))
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
  ([facts]
   (let [schema (:schema @state)
         facts-v (if (coll? (first facts)) facts (vector facts))
         tuples (mapcat util/insertable facts-v)
         unique-attrs (unique-identity-attrs schema tuples)
         unique-values (unique-value-attrs schema tuples)
         existing-unique-identity-facts (mapcat #(util/facts-where (:session @state) %)
                                          unique-attrs)
         existing-unique-value-facts (unique-value-facts (:sesson @state) tuples unique-values)
         existing-unique-facts (into existing-unique-identity-facts existing-unique-value-facts)
         _ (println "Schema-insert unique values " unique-values)
         _ (println "Schema-insert unique value facts " existing-unique-value-facts)
         _ (println "Schema-insert removing " existing-unique-facts)
         _ (println "Schema-insert inserting " tuples)
         next-session (if (empty? existing-unique-facts)
                        (-> (:session @state)
                          (cr/insert-all tuples))
                        (-> (:session @state)
                          (util/retract existing-unique-facts)
                          (cr/insert-all tuples)))]
     next-session))
  ([session facts] (schema-insert facts)))

(defn swap-session! [next]
  (swap! state assoc :session next))

;; TODO. Find equivalent in CLJ
(defn lens [a path]
  #?(:clj (atom (get-in @a path))
     :cljs (r/cursor a path)))

(defn register
  [req]
  (let [id (util/guid)
        name (first req)
        inserted-sub (-> (:session @state) (schema-insert [id ::sub/request name]))
        lens (lens store [id ::sub/response])]
    (advance-session! inserted-sub)
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
         _ (println "New sub name / existing if any" name existing)]
         ;_ (println "Existing / all subs" existing (:subscriptions @state))]
     (if existing
       (:lens existing)
       (register req)))))


;(defn create-session-ch!
;  ([] (binding [session-ch session-ch]
;        (set! session-ch (chan 1)))
;  ([ch] (set! session-ch ch)))
;
;(defn create-changes-ch! [ch]
;  ([] (binding [changes-ch changes-ch]
;        (set! changes-ch (chan 1)))
;  ([ch] (set! changes-ch ch)))

;(defn query? [x] (and (seq x) (= :where (first x))))
;
;(defn parse-query-params [exprs]
;  (println "Parsing query params" exprs)
;  (mapcat
;    (fn [expr]
;       (println "Expr" expr (s/valid? ::lang/variable-binding (second expr)))
;       (filter #(s/valid? ::lang/variable-binding %) expr))
;    exprs))
;
;(defn run-query [exprs]
;  (println "Run query")
;  (let [params (parse-query-params exprs)]
;    (println "params")))
;    ;(clara.rules/query @session-atom
;    ;  (clara.rules.dsl/parse-query params exprs)))) ;TODO. Used in clara test but passed
;    ; directly to mk-session
;
;(defn send-with-query [[op exprs]]
;  (println "Send with query" op exprs)
;  (let [query (second exprs)
;        query-result (run-query query)]
;    (condp = op
;      :remove (swap! state update :session (fn [old] (-> old (util/retract query-result))))
;      :replace  (swap! state update :session
;                  (fn [old]
;                   (-> old
;                     (util/retract query-result)
;                     (util/insert (second double))))))))

(defn then
  ([op facts]
   (condp = op
     :add (advance-session! (schema-insert facts))
     (println "Unsupported op keyword " op)))
  ([facts] (then :add facts)))

;(defn send [& exprs]
;  (let [msgs (reduce
;                  (fn [acc cur]
;                     (if (query? (second cur))
;                       (conj acc [:__query (vector (first cur) (second (second cur)))])
;                       acc))
;                 [] (partition 2 exprs))]
;    (for [msg msgs]
;      (condp (first msg)
;        :__query (send-with-query (second msg))
;        :add (swap! state update :session (fn [old] (-> old (util/insert (second msg)))))
;        :remove (swap! state update :session (fn [old] (-> old (util/retract (second msg)))))
;        :replace  (swap! state update :session
;                    (fn [old] (-> old
;                                (util/retract (second msg))
;                                (util/insert (second msg)))))))))

(defn start! [options]
  (let [opts (or options (hash-map))]
    (swap-session! (l/replace-listener (:session opts)))
    (init-schema (into schema/libx-schema (:schema opts)))
    (advance-session! (schema-insert (:facts opts)))))


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
;(advance-session! next-session)

;; Schema
;(init-schema app-schema)
;(schema-insert facts)

;; Read
;(:session @state)
;@store
;(:subscriptions @state)
;(:schema @state)

;(def ch (chan))
;(def ch2 (chan))
;;
;;(defn myfunc []
;;  (do
;;    (doseq [x (range 0 10)]
;;      (go (>! ch x) (println "foo")))
;      ;(put! ch x)
;    ;(println "bar"))
;(defn myfunc []
;  (go
;    (doseq [x (range 0 10)]
;      ;(go (>! ch x) (println "foo"))
;      (put! ch x))
;    (<! ch2)
;    (println "never")))
;
;(defn rec-loop []
;  (go-loop []
;    (let [x (<! ch)]
;      (do (println x) (recur)))))
;
;(def rec (rec-loop))
;(myfunc)
;(put! ch "Hey")
@store

(:subscriptions @state)
(find-sub-by-name [:todo-app])