(ns todomvc.events
  (:require
    [todomvc.db :refer [todos->local-store]]
    [re-frame.core :refer [reg-event-db reg-event-fx inject-cofx path trim-v
                           after debug]]
    [todomvc.rules :refer [todo-tx
                           visibility-filter-tx
                           toggle-tx
                           clear-completed-action
                           find-all-done]]
    [todomvc.util :refer [entity entityv entities-where map->tuple facts-where insert-fire!]]
    [clara.rules :refer [insert-all insert retract fire-rules query]]
    [cljs.spec :as s]))


;; -- Interceptors --------------------------------------------------------------
;;

(defn check-and-throw
  "throw an exception if db doesn't match the spec"
  [a-spec db]
  (when-not (s/valid? a-spec db)
    (throw (ex-info (str "spec check failed: " (s/explain-str a-spec db)) {}))))

;; Event handlers change state, that's their job. But what happens if there's
;; a bug which corrupts app state in some subtle way? This interceptor is run after
;; each event handler has finished, and it checks app-db against a spec.  This
;; helps us detect event handler bugs early.
(def check-spec-interceptor (after (partial check-and-throw :todomvc.db/db)))

;; this interceptor stores todos into local storage
;; we attach it to each event handler which could update todos
(def ->local-store (after todos->local-store))

;; Each event handler can have its own set of interceptors (middleware)
;; But we use the same set of interceptors for all event habdlers related
;; to manipulating todos.
;; A chain of interceptors is a vector.
(def todo-interceptors [check-spec-interceptor              ;; ensure the spec is still valid
                        (path :todos)                       ;; 1st param to handler will be the value from this path
                        ->local-store                       ;; write todos to localstore
                        (when ^boolean js/goog.DEBUG debug) ;; look in your browser console for debug logs
                        trim-v])                            ;; removes first (event id) element from the event vec


;; -- Event Handlers ----------------------------------------------------------

(reg-event-fx
  :initialise-db
  (fn [{:keys [db]} [_ session]]
    (prn "Session initializing" session)
    {:db session}))

(defn old-showing [session]
  (println "Old showing.." (first (entities-where session :ui/visibility-filter)))
  (first (entities-where session :ui/visibility-filter)))
(reg-event-db
  :set-showing
  (fn [session [_ new-filter-kw]]
    (prn "Session in set showing" session)
    (prn "New filter keyword is" new-filter-kw)
    (let [old         (map->tuple (old-showing session))
          removed     (retract session (first old))
          with-new    (insert-all removed (map->tuple
                                            (visibility-filter-tx (random-uuid) new-filter-kw)))
          new-session (fire-rules with-new)]
      (prn "old " old)
      (prn "removed " removed)
      (prn "with-new " with-new)
      (prn "new session " new-session)
      new-session)))

(defn get-todos [session]
  (entities-where session :todo/title))
(reg-event-db
  :add-todo
  (fn [session [_ text]]
    (let [id   (random-uuid)
          todo (todo-tx id text nil)]
      (fire-rules (insert session (first (map->tuple todo)))))))

(reg-event-db
  :toggle-done
  (fn [session [_ id]]
    (let [statuses (entities-where session id)]
      (-> session
        (retract (map map->tuple statuses))
        (fire-rules)))))

(reg-event-db
  :save
  (fn [session [_ id title]]
    (prn ":save action id" id)
    (prn ":save action title" title)
    (let [todo         (entity session id)
          updated-todo (assoc todo :todo/title title)]
      (prn ":save session" session)
      (prn ":save todo" todo)
      (prn ":save updated-todo" updated-todo)
      (-> session
        (retract (first (map->tuple todo)))
        (insert updated-todo)
        (fire-rules)))))

(reg-event-db
  :delete-todo
  (fn [session [_ id]]
    (let [todov (entityv session id)]
      (println "Deleting" todov)
      (fire-rules (apply (partial retract session) todov)))))


(defn get-all-done [session]
  (:?todos (first (query session find-all-done))))
(reg-event-db
  :clear-completed
  (fn [session] (-> session
                  (insert (first (map->tuple clear-completed-action)))
                  (fire-rules))))

(reg-event-db
  :complete-all-toggle
  (fn [session _]
    (-> session
      (insert (first (map->tuple (toggle-tx (random-uuid) true))))
      (fire-rules))))
