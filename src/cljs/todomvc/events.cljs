(ns todomvc.events
  (:require
    [todomvc.db :refer [todos->local-store]]
    [re-frame.core :refer [reg-event-db reg-event-fx inject-cofx path trim-v
                           after debug]]
    [todomvc.rules :refer [todo-tx
                           visibility-filter-tx
                           toggle-tx
                           find-all-done]]
    [todomvc.util :refer [entity entities-where map->tuple]]
    [clara.rules :refer [insert retract fire-rules query]]
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
  (first (entities-where session :ui/visibility-filter)))
(reg-event-db
  :set-showing
  (fn [session [_ new-filter-kw]]
    (prn "Session in set showing" session)
    (prn "New filter keyword is" new-filter-kw)
    (let [old         (old-showing session)
          removed     (retract session (map->tuple old))
          with-new    (insert removed (map->tuple (visibility-filter-tx (random-uuid) new-filter-kw)))
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
    (let [id    (random-uuid)
          todo  (todo-tx id text nil)]
      (fire-rules (insert session (map->tuple todo))))))

(defn get-todo [session id]
  (entity session id))
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
    (let [todo         (get-todo session id)
          updated-todo (assoc todo :todo/title title)]
      (prn ":save session" session)
      (prn ":save todo" todo)
      (prn ":save updated-todo" updated-todo)
      (-> session
        (retract (map->tuple todo))
        (insert updated-todo)
        (fire-rules)))))

(reg-event-db
  :delete-todo
  (fn [session [_ id]]
    (let [todo (get-todo session id)]
      (-> session
        (retract todo)
        (fire-rules)))))

(defn get-all-done [session]
  (:?todos (first (query session find-all-done))))
(reg-event-db
  :clear-completed
  (fn [session _]
    (let [all-done (get-all-done session)
          removed  (apply retract session all-done)]
      (prn ":clear completed all-done" all-done)
      (prn ":clear completed removed" removed)
      (fire-rules removed))))

(reg-event-db
  :complete-all-toggle
  (fn [session _]
    (-> session
      (insert (map->tuple (toggle-tx (random-uuid) true)))
      (fire-rules))))
