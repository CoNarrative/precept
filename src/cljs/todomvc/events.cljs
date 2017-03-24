(ns todomvc.events
  (:require
    [re-frame.core :refer [reg-event-db reg-event-fx inject-cofx path trim-v after debug]]
    [todomvc.facts :refer [todo visibility-filter mark-all-done-action clear-completed-action]]
    [todomvc.rules :refer [find-all-done]]
    [todomvc.util :refer [insert
                          insert-fire!
                          retract
                          retract-fire!
                          replace!
                          entity
                          entityv
                          entities-where
                          facts-where]]
    [clara.rules :refer [query fire-rules]]))

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
    (let [old         (old-showing session)
          removed     (retract session old)
          with-new    (insert removed (visibility-filter (random-uuid) new-filter-kw))
          new-session (fire-rules with-new)]
      (prn "old " old)
      (prn "removed " removed)
      (prn "with-new " with-new)
      (prn "new session " new-session)
      new-session)))

; TODO. Above should be more like the following, using lib's versions of remove and insert
; to cut down on boilerplate when possible
;(reg-event-db
;  :set-showing
;  (fn [session [_ new-filter-kw]]
;    (prn "Session in set showing" session)
;    (prn "New filter keyword is" new-filter-kw)
;    (println "Filter from session" (first (entities-where session :ui/visibility-filter)))
;    (let [filter (first (entities-where session :ui/visibility-filter))
;          removed     (retract session filter)]
;      (insert-fire! removed (visibility-filter (random-uuid) new-filter-kw)))))

(reg-event-db :add-todo
  (fn [session [_ text]] (insert-fire! session (todo (random-uuid) text nil))))

;TODO. Convert to action pattern
(reg-event-db :toggle-done
  (fn [session [_ id]]
    (let [done (:todo/done (entity session id))
          fact [id :todo/done :tag]]
      (if done
        (retract-fire! session fact)
        (insert-fire! session fact)))))

;TODO. Convert to action pattern
(reg-event-db :save
  (fn [session [_ id title]]
    (let [todo         (entity session id)
          updated-todo (assoc todo :todo/title title)]
      (-> session
        (replace! todo updated-todo)
        (fire-rules)))))

(reg-event-db :delete-todo
  (fn [session [_ id]]
    (let [todov (entityv session id)]
      (println "Deleting" todov)
      (fire-rules (retract session todov)))))

(reg-event-db :clear-completed
  (fn [session] (insert-fire! session clear-completed-action)))

(reg-event-db :complete-all-toggle
  (fn [session] (insert-fire! session (mark-all-done-action))))
