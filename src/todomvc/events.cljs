(ns todomvc.events
  (:require
    [todomvc.db    :refer [default-value todos->local-store]]
    [re-frame.core :refer [reg-event-db reg-event-fx inject-cofx path trim-v
                           after debug]]
    [todomvc.rules :refer [find-showing
                           find-todo
                           find-todos
                           find-max-id
                           find-all-done
                           ->Todo Todo
                           ->Showing Showing]]
    [clara.rules :refer [query insert retract fire-rules]]
    [cljs.spec     :as s]))


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
(def todo-interceptors [check-spec-interceptor               ;; ensure the spec is still valid
                        (path :todos)                        ;; 1st param to handler will be the value from this path
                        ->local-store                        ;; write todos to localstore
                        (when ^boolean js/goog.DEBUG debug)  ;; look in your browser console for debug logs
                        trim-v])                             ;; removes first (event id) element from the event vec


;; -- Helpers -----------------------------------------------------------------

(defn allocate-next-id
  "Returns the next todo id.
  Assumes todos are sorted.
  Returns one more than the current largest id."
  [db]
  ((fnil inc 0) (:?id (first (query (:state db) find-max-id)))))


;(defn allocate-next-id
;  "Returns the next todo id.
;  Assumes todos are sorted.
;  Returns one more than the current largest id."
;  [todos]
;  ((fnil inc 0) (last (keys todos))))

;; -- Event Handlers ----------------------------------------------------------

;; usage:  (dispatch [:initialise-db])
(reg-event-fx                     ;; on app startup, create initial state
  :initialise-db                  ;; event id being handled
  ;[(inject-cofx :local-store-todos)]
     ;; obtain todos from localstore
 ;  check-spec-interceptor                                  ;; after the event handler runs,
 ; check that app-db matches the spec
  (fn [{:keys [db]} [_ session]]                    ;; the handler being registered
    (prn "Session initializing" session)
    {:db (assoc db :state session)}))  ;; all hail the new state


(defn old-showing [session]
  (:?showing (first (query session find-showing))))
;; usage:  (dispatch [:set-showing  :active])
(reg-event-db                     ;; this handler changes the todo filter
  :set-showing                    ;; event-id

  ;; this chain of two interceptors wrap the handler
  ;[check-spec-interceptor (path :showing) trim-v]

  ;; The event handler
  ;; Because of the path interceptor above, the 1st parameter to
  ;; the handler below won't be the entire 'db', and instead will
  ;; be the value at a certain path within db, namely :showing.
  ;; Also, the use of the 'trim-v' interceptor means we can omit
  ;; the leading underscore from the 2nd parameter (event vector).
  ;(fn [old-keyword [new-filter-kw]]  ;; handler
  ;  new-filter-kw]                  ;; return new state for the path
  (fn [session [_ new-filter-kw]]  ;; handler
    (prn "Session in set showing" session)
    (prn "New filter keyword is" new-filter-kw)
    (let [old (old-showing (:state session))
          removed (retract (:state session) old)
          with-new (insert removed (->Showing new-filter-kw))
          new-session (fire-rules with-new)]
        (prn "old " old)
        (prn "removed " removed)
        (prn "with-new " with-new)
        (prn "new session " new-session)
      {:state new-session})))

(defn get-todos [db]
  (:?todos (first (query (:state db) find-todos))))
(reg-event-db
  :add-todo
  (fn [db [_ text]]
    (let [session (:state db)
          todos (get-todos db)
          id (allocate-next-id db)
          todo (->Todo id text false)]
      (prn "todos" todos)
      {:state (fire-rules (insert session todo))})))

; usage:  (dispatch [:add-todo  "Finish comments"])
;(reg-event-db                     ;; given the text, create a new todo
;  :add-todo
;
;  ;; The standard set of interceptors, defined above, which we
;  ;; apply to all todos-modifiing event handlers. Looks after
;  ;; writing todos to local store, etc.
;  todo-interceptors
;
;  ;; The event handler function.
;  ;; The "path" interceptor in `todo-interceptors` means 1st parameter is :todos
;  (fn [todos [text]]
;    (let [id (allocate-next-id todos)]
;      (assoc todos id {:id id :title text :done false}))))

(defn get-todo [db id]
  (:?todo (first (query (:state db) find-todo :?id id))))

(reg-event-db
  :toggle-done
  (fn [db [_ id]]
    (let [session (:state db)
          todo (get-todo db id)
          updated-todo (update todo :done not)]
      {:state (-> session
                (retract todo)
                (insert updated-todo)
                (fire-rules))})))

;(reg-event-db
;  :toggle-done
;  todo-interceptors
;  (fn [todos [id]]
;    (update-in todos [id :done] not)))


(reg-event-db
  :save
  (fn [db [_ id title]]
    (prn ":save action id" id)
    (prn ":save action title" title)
    (let [session (:state db)
          todo (get-todo db id)
          updated-todo (assoc todo :title title)]
      (prn ":save session" session)
      (prn ":save todo" todo)
      (prn ":save updated-todo" updated-todo)
      {:state (-> session
                (retract todo)
                (insert updated-todo)
                (fire-rules))})))

(reg-event-db
  :delete-todo
  (fn [db [_ id]]
    (let [session (:state db)
          todo (get-todo db id)]
      {:state (-> session
                (retract todo)
                (fire-rules))})))

;(reg-event-db
;  :delete-todo
;  todo-interceptors
;  (fn [todos [id]]
;    (dissoc todos id)))

(defn get-all-done [db]
  (:?todos(first (query (:state db) find-all-done))))
(reg-event-db
  :clear-completed
  (fn [db _]
    (let [session (:state db)
          all-done (get-all-done db)
          removed (apply retract session all-done)]
      (prn ":clear completed all-done" all-done)
      (prn ":clear completed removed" removed)
      {:state (fire-rules removed)})))

;(reg-event-db
;  :clear-completed
;  todo-interceptors
;  (fn [todos _]
;    (->> (vals todos)                ;; find the ids of all todos where :done is true
;         (filter :done)
;         (map :id)
;         (reduce dissoc todos)})))    ;; now delete these ids


(reg-event-db
  :complete-all-toggle
  todo-interceptors
  (fn [todos _]
    (let [new-done (not-every? :done (vals todos))]   ;; work out: toggle true or false?
      (reduce #(assoc-in %1 [%2 :done] new-done)
              todos
              (keys todos)))))
