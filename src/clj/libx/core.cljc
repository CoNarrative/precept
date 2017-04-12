(ns ^:figwheel-always libx.core
    (:refer-clojure :exclude [send])
    (:require [libx.util :refer [entity-tuples->entity-map] :as util]
              [libx.listeners :refer [ops] :as l]
              [clara.rules :refer [fire-rules insert! insert-all!]]
              [clara.rules.accumulators :as acc]
              [libx.lang :as lang]
              [libx.tuplerules :refer [def-tuple-session def-tuple-rule]]
              [clojure.spec :as s]
      #?(:clj [clojure.core.async :refer [<! >! put! take! chan go-loop]])
      #?(:clj [reagent.ratom :as rr])
      #?(:cljs [cljs.core.async :refer [put! take! chan <! >!]])
      #?(:cljs [reagent.core :as r]))
  #?(:cljs (:require-macros [cljs.core.async.macros :refer [go go-loop]])))

; TODO. keep vector of states if dev
;(def dev? true)

(defn mk-ratom [args]
  #?(:clj (atom args) :cljs (r/atom args)))

(def current-session (atom nil))

(def store (mk-ratom {}))

(def subscriptions (atom {}))

(def changes-ch (chan 1))

(def session-ch (chan 1))

(defn lens [a path]
  #?(:clj (atom (get-in @a path)) ;; for testing in clj only
     :cljs (r/cursor a path)))

(defn mark-as-sub [path]
  (keyword (str "sub/" (name (first path)))))

(defn register
  ([path] (register path subscriptions store current-session session-ch))
  ([path _subscriptions _store _current-session _session-ch]
   (println "Registering " path)
   (if-let [existing (get @_subscriptions path)]
      existing
      (let [lens (lens _store path)
            inserted-sub (-> @_current-session
                           (l/replace-listener) ;; subs get written to store
                           (util/insert [-1 (mark-as-sub path) :default]))
            _ (println "Registration ops" (ops inserted-sub))]
        (put! _session-ch inserted-sub)
        (swap! _subscriptions assoc path lens)
        lens))))


;TODO. Return vector of maps if multiple paths provided, otherwise single map
; Verify cursors update on change to @state and that change propagates to component
(defn subscribe
  "Returns ratom of reagent cursors, one for each path in paths.
   * paths - vector containing sub requests, any of which may contain parameters.
             Examples: `[:kw]` `[:kw \"someval\"]` `[[:kw \"someval\"] [:kw2]]`
  Returned ratom will contain a single map for single subscription or vector of maps for multiple"
  ([paths] (subscribe paths subscriptions store session-ch))
  ([paths _subscriptions _store _session-ch]
   (let [existing-subs (select-keys @_subscriptions paths)
         new-paths (remove (set (keys existing-subs)) paths)
         requested-subs (reduce
                          (fn [acc path]
                            (let [vector-path (if (vector? path) path (vector path))]
                              (conj acc (register vector-path))))
                          (or (vals existing-subs) [])
                          new-paths)]
      (mk-ratom (if (second requested-subs)
                    requested-subs
                    (first requested-subs))))))

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

(defn add [atom changes]
  "Merges changes into atom"
  (swap! atom merge (dissoc changes :op :db/id)))

(defn del [atom changes]
  "Removes keys in change from atom"
  (swap! atom (fn [m] (apply dissoc m (keys (clean-changes changes))))))

(defn create-change->store-router! [in-ch _store]
  "Reads changes from in channel and updates store
   * `in-ch` - core.async channel
   * `store` - atom"
  (go-loop []
    (let [change (<! in-ch)
          id (:db/id change)
          op (:op change)
          _ (println "Change id op atom" change id op)]
      (condp = op
        :add (do (add _store change) (recur))
        :remove (do (del _store change) (recur))
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
    ;(clara.rules/query @current-session
    ;  (clara.rules.dsl/parse-query params exprs)))) ;TODO. Used in clara test but passed
    ; directly to mk-session

(defn send-with-query [[op exprs]]
  (println "Send with query" op exprs)
  (let [query (second exprs)
        query-result (run-query query)]
    (condp = op
      :remove (reset! current-session (-> @current-session (util/retract query-result)))
      :replace  (reset! current-session
                 (-> @current-session
                   (util/retract query-result)
                   (util/insert (second double)))))))

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
        :add (reset! current-session (-> @current-session (util/insert (second msg))))
        :remove (reset! current-session (-> @current-session (util/retract (second msg))))
        :replace  (reset! current-session
                   (-> @current-session
                     (util/retract (second msg))
                     (util/insert (second msg))))))))

(defn create-session->change-router!
  "Reads changes from session channel, fires rules, and puts resultant changes
  on changes channel. Updates session state atom with new session."
  [in-ch out-ch _current-session]
  (go-loop []
    (let [session (<! in-ch)
          next (fire-rules session)
          changes (embed-op (ops next))
          _ (println "Session changes" changes)]
      (do
        (reset! _current-session (l/replace-listener next))
        (doseq [change changes]
          (put! out-ch change)))
      (recur))))

(defn start! [options]
  (let [opts (or options (hash-map))]
    (do
      (reset! current-session (:session opts))
      ;(create-session-ch! (or (:session-ch opts) (chan 1)))
      ;(create-changes-ch! (or (:changes-ch opts) (chan 1)))
      (create-session->change-router! session-ch changes-ch current-session)
      (create-change->store-router! changes-ch store))))

(defn put-session!
  "Returns session"
  [session]
  (put! session-ch session)
  session)


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; test-area
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def-tuple-rule subs-footer-controls
  [:exists [:sub/footer]]
  [[_ :done-count ?done-count]]
  [[_ :active-count ?active-count]]
  =>
  (insert-all! [[-1 :lens/footer {:active-count ?active-count
                                  :done-count ?done-count}]]))

(def-tuple-rule subs-task-list
  [:exists [:sub/task-list]]
  [?visible-todos <- (acc/all) :from [:todo/visible]]
  [[_ :active-count ?active-count]]
  =>
  (insert-all! [[-1 :lens/task-list {:visible-todos (libx.util/tuples->maps ?visible-todos)
                                     :all-complete? (> ?active-count 0)}]]))
(def-tuple-rule subs-todo-app
  [:exists [:sub/todo-app]]
  [?todos <- (acc/all) :from [:todo/title]]
  =>
  (println "All todos" ?todos)
  (insert! [-1 :lens/todo-app (libx.util/tuples->maps (:todos ?todos))]))

;(comment
(def-tuple-session my-sess 'libx.core)

(def x23 (create-session->change-router! session-ch changes-ch current-session))
(def y (create-change->store-router! changes-ch store))

;; Reset
(reset! current-session my-sess)
(reset! store {})
(reset! subscriptions {})
(reset! current-session (l/replace-listener my-sess))

(def my-sub (subscribe [[:task-list] [:footer]]))

; Repeatable ;;;;
(def next-session (util/insert @current-session
                          [[-1 :active-count 7]
                           [-2 :done-count 1]
                           [-3 :todo/visible :tag]
                           [-4 :todo/title "Hi"]]))
(put-session! next-session)
@store
@subscriptions
@current-session
(ops @current-session)
(l/all-listeners @current-session)
;;;;;;;;;;;;;;;;;

;; Problems
;; * Subs that are registered afterMUST be registered before any rules fire. Otherwise they won't
;; be
;; notified of
;; changes.
;;   Not clear right now how to best solve this. Rules should fire once subscription is inserted.

;; TODO.
;; * Key store by sub's vector form (to allow params). Subscriptions atom already uses this format.
;; * Decide whether store only keeps "lens"-prefixed changes, or whether we should
;;   continue to maintain a record of the current state (regardless of whether there is a
;;   subscription for it)
;; * If a "lens" change is received, instead of putting it in the store, we could update the
;;   subscription directly. However, this would prevent shared subscriptions because we would not
;;   be maintaing a single source of truth accessible to multiple observers
;; * Lenses should return maps where they currently return tuples
