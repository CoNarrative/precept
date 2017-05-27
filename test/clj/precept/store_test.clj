(ns precept.store-test)
;    (:require [clojure.test :refer [use-fixtures is deftest testing run-tests]]
;              [clara.rules.accumulators :as acc]
;              [clara.rules :as cr]
;              [clojure.core.async :refer [chan go >! <! <!!] :as async]
;              [precept.core :refer [store state] :as core]
;              [precept.spec.sub :as sub]
;              [precept.listeners :as l]
;              [precept.schema-fixture :refer [test-schema]]
;              [precept.util :refer [guid clara-tups->tups insert retract qa- entityv]]
;              [precept.tuplerules :refer [def-tuple-rule def-tuple-query def-tuple-session]]))
;
;(def-tuple-rule todo-is-visible-when-filter-is-all
;  [[_ :visibility-filter :all]]
;  [[?e :todo/title]]
;  =>
;  (cr/insert! [?e :todo/visible :tag]))
;
;(def-tuple-rule todo-is-visile-when-filter-is-done-and-todo-done
;  [[_ :visibility-filter :done]]
;  [[?e :todo/done]]
;  =>
;  (cr/insert! [?e :todo/visible :tag]))
;
;(def-tuple-rule todo-is-visible-when-filter-active-and-todo-not-done
;  [[_ :visibility-filter :active]]
;  [[?e :todo/title]]
;  [:not [?e :todo/done]]
;  =>
;  (cr/insert! [?e :todo/visible :tag]))
;
;(def-tuple-rule toggle-all-complete
;  [:exists [:ui/toggle-complete]]
;  [[?e :todo/title]]
;  [:not [?e :todo/done]]
;  =>
;  (println "Marked done via toggle complete:" ?e)
;  (cr/insert-unconditional! [?e :todo/done :tag]))
;
;(def-tuple-rule remove-toggle-complete-when-all-todos-done
;  [?toggle <- :ui/toggle-complete]
;  [?total <- (acc/count) :from [:todo/title]]
;  [?total-done <- (acc/count) :from [:todo/done]]
;  [:test (= ?total ?total-done)]
;  =>
;  (println "Total todos: " ?total)
;  (println "Total done: " ?total-done)
;  (println "Retracting toggle-all-complete action: " ?toggle)
;  (cr/retract! ?toggle))
;
;(def-tuple-rule no-done-todos-when-clear-completed-action
;  [:exists [:ui/clear-completed]]
;  [[?e :todo/title]]
;  [[?e :todo/done]]
;  [?entity <- (acc/all) :from [?e :all]]
;  =>
;  (println "Retracting entity " ?entity)
;  (doseq [tuple ?entity] (cr/retract! tuple)))
;
;(def-tuple-rule clear-completed-action-is-done-when-no-done-todos
;  [?action <- :ui/clear-completed]
;  [:not [:exists [:todo/done]]]
;  =>
;  (println "Clear-completed action finished. Retracting " ?action)
;  (cr/retract! ?action))
;
;(def-tuple-rule print-all-facts
;  [?fact <- [?e]]
;  =>
;  (println "FACT" ?fact))
;
;(def-tuple-rule find-done-count
;  [?done <- (acc/count) :from [:todo/done]]
;  [?total <- (acc/count) :from [:todo/title]]
;  =>
;  (println "done active count" ?done (- ?total ?done))
;  (cr/insert-all!
;    [[(guid) :done-count ?done]
;     [(guid) :active-count (- ?total ?done)]]))
;
;(def-tuple-rule subs-footer-controls
;  [:exists [?e ::sub/request [:footer]]]
;  [[_ :done-count ?done-count]]
;  [[_ :active-count ?active-count]]
;  [[_ :visibility-filter ?visibility-filter]]
;  =>
;  (println "Inserting footer response")
;  (let [id (guid)]
;    (cr/insert!
;      [(guid) ::sub/response
;              {:active-count ?active-count
;               :done-count ?done-count
;               :visibility-filter ?visibility-filter}])))
;
;(def-tuple-rule subs-task-list
;  [:exists [?e ::sub/request [:task-list]]]
;  [?visible-todos <- (acc/all) :from [:todo/visible]]
;  [[_ :active-count ?active-count]]
;  =>
;  (println "Inserting task list response")
;  (let [id (guid)]
;    (cr/insert-all!
;      [[id ::sub/response
;        {:visible-todos (precept.util/tuples->maps ?visible-todos)
;         :all-complete? (> ?active-count 0)}]
;       [?e ::sub/response-id id]])))
;
;(def-tuple-rule subs-todo-app
;  [:exists [?e ::sub/request [:todo-app]]]
;  [?todos <- (acc/all) :from [:todo/title]]
;  =>
;  (println "Inserting all-todos response" ?todos)
;  (cr/insert! [(guid) ::sub/response (precept.util/tuples->maps ?todos)]))
;
;(def-tuple-query find-all-facts
;  []
;  [?facts <- (acc/all) :from [:all]])
;
;(def-tuple-session my-session 'precept.store-test)
;
;(defn reset-store []
;  (reset! store {}))
;
;(defn reset-state []
;  (reset! state core/initial-state))
;
;(defn reset-session []
;  (swap! state assoc :session nil))
;(defn reset-session-history []
;  (swap! state assoc :session-history []))
;
;(def id (guid))
;(def initial-facts
;  [[id :todo/visible :tag]
;   [id :todo/title "Hi"]
;   [(guid) :visibility-filter :done]])
;
;;(use-fixtures :once
;;  (fn [_]
;;    (reset-session)
;;    (reset-store)]])
;(defn test-async
;  "Asynchronous test awaiting ch to produce a value or close."
;  [ch]
; (<!! ch))
;
;(defn reset-all []
;  (reset-state)
;  (reset-store)
;  nil)
;
;(reset-all)
;@store
;@state
;
;(deftest store-session-synchronization
;  (testing "Initial state"
;    (is (= nil (:session @state)))
;    (is (= {} @store)))
;  (testing "Start"
;    (let [ch (chan)]
;     (go (>! ch
;          (core/start!
;            {:session my-session
;             :db-schema test-schema
;             :facts initial-facts})))
;     (test-async
;       (go (is (= @store {} (<! ch))))))))
;
;
;(run-tests)
;
;;(clojure.tools.namespace.repl/refresh-all)