(ns libx.todomvc.events
  (:require
    [re-frame.core :refer [reg-event-db reg-event-fx inject-cofx path trim-v after debug]]
    [libx.todomvc.facts :refer [todo visibility-filter mark-all-done-action clear-completed-action]]
    [libx.util :refer [insert
                          insert-fire
                          retract
                          retract-fire
                          replace!
                          entity
                          entityv
                          entities-where
                          facts-where]]
    [libx.listeners :refer [add-listener remove-fact-listeners] :as l]
    [libx.todomvc.add-me :refer [replace-listener ops]]
    [clara.rules :refer [fire-rules]]))

(reg-event-fx
  :initialise-db
  (fn [{:keys [db]} [_ session]]
    (prn "Session initializing" session)
    {:db (replace-listener session)}))

(defn old-showing [session]
  (first (entities-where session :ui/visibility-filter)))

(reg-event-db :set-showing
  (fn [session [_ new-filter-kw]]
    (let [old         (old-showing session)
          new (visibility-filter (random-uuid) new-filter-kw)
          next-state (-> session
                       (replace-listener)
                       (replace! old new)
                       (fire-rules))
          changes (ops next-state)
          _ (println "Changes" changes)]
      next-state)))

(reg-event-db :add-todo
  (fn [session [_ text]] (insert-fire session (todo (random-uuid) text nil))))

;TODO. Convert to action pattern
(reg-event-db :toggle-done
  (fn [session [_ id]]
    (let [done (:todo/done (entity session id))
          fact [id :todo/done :tag]]
      (if done
        (retract-fire session fact)
        (insert-fire session fact)))))

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
  (fn [session] (insert-fire session clear-completed-action)))

(reg-event-db :complete-all-toggle
  (fn [session] (insert-fire session (mark-all-done-action))))
