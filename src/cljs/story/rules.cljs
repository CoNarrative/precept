(ns story.rules
  (:require [clara.rules.accumulators :as acc]
            [clara.rules :as cr]
            [libx.core :refer [notify!]]
            [libx.spec.sub :as sub]
            [story.schema :refer [app-schema]]
            [clojure.core.reducers :as r]
            [libx.util :refer [insert! insert-unconditional! retract! attr-ns guid Tuple]]
            [libx.tuplerules :refer-macros [deflogical store-action def-tuple-session def-tuple-rule]]
            [libx.schema :as schema]
            [libx.util :as util]))

;(def-tuple-rule all-facts
;  [?fact <- [:all]]
;  =>
;  (println "FACT" (into [] (vals ?fact))))

(defn trace [& args]
  (apply prn args))

;(def-tuple-rule all-facts

; a "reducer" because it saves perma-state
(def-tuple-rule save-the-fact-that-mouse-is-down {:group :action}
  [[_ :action/type :mouse/down]] => (insert-unconditional! [0 :mouse/left-pressed true]))


(def-tuple-rule dom-node-moves-with-mouse-when-drag-started
  [[_ :drag/start ?e]]
  [[_ :mouse/x ?x]]
  [[_ :mouse/y ?y]]
  =>
  (trace "Inserting translate")
  (insert! [[?e :dom/translateX ?x]
            [?e :dom/translateY ?y]]))

(def-tuple-rule detect-when-dragging
  [[_ :mouse/down]]
  [[_ :dom/translateX ?x1]]
  [[_ :dom/translateY ?y1]]
  [[_ :mouse/x ?x2]]
  [[_ :mouse/y ?y2]]
  =>
  (trace "Dragging is true")
  (insert! [(guid) :drag/dragging? :tag]))


;; Calculations

(defn by-fact-id
  ([]
   (acc/accum
     {:initial-value []
      :reduce-fn (fn [acc cur] (sort-by :t (conj acc cur)))
      :retract-fn (fn [acc cur] (sort-by :t (remove #(= cur %) acc)))}))
  ([k]
   (acc/accum
     {:initial-value []
      :reduce-fn (fn [acc cur] (sort-by :t (conj acc (k cur))))
      :retract-fn (fn [acc cur] (sort-by :t (remove #(= (k cur %)) acc)))})))

(def-tuple-rule create-list-of-visible-todos
  {:group :report}
  [?eids <- (by-fact-id :e) :from [:part/visible]]
  [:test (seq ?eids)]
  =>
  (println "List!" ?eids)
  (insert! [(guid) :todos/by-last-modified*order ?eids])
  (doseq [x ?eids]
    (insert! [(guid) :todos/by-last-modified*eid x])))

(def-tuple-rule update-list-of-visible-todos
  {:group :report}
  [[_ :todos/by-last-modified*eid ?e]]
  [?entity <- (acc/all) :from [?e :all]]
  =>
  (println "Entity list!" ?entity)
  (insert! [(guid) :todos/by-last-modified*item ?entity]))

(def-tuple-rule order-list-of-visible-todos
  {:group :report}
  [:exists [?e ::sub/request :task-list]]
  [[_ :todos/by-last-modified*order ?eids]]
  [?items <- (acc/all :v) :from [:todos/by-last-modified*item]]
  [:test (seq ?eids)] ;; TODO. Investigate whether us or Clara
  =>
  (let [items (group-by :e (flatten ?items))
        ordered (vals (select-keys items (into [] ?eids)))
        entities (util/entity-Tuples->entity-maps ordered)]
    (println "Entities" entities)
    ; Following are equivalent excepting the second results in order being lost:
    ;(notify! :task-list (fn [x] (if (map? x)
    ;                              (assoc x :visible-todos entities)
    ;                              {:visible-todos entities})
    (insert! [?e ::sub/response {:visible-todos entities}])))

;; Subscription handlers
(def-tuple-rule subs-footer-controls
  [:exists [?e ::sub/request :footer]]
  [[_ :done-count ?done-count]]
  [[_ :active-count ?active-count]]
  [[_ :ui/visibility-filter ?visibility-filter]]
  =>
  (trace "Inserting footer response" ?e)
  (insert!
    [?e ::sub/response
        {:active-count ?active-count
         :done-count ?done-count
         :visibility-filter ?visibility-filter}]))

(def-tuple-rule subs-part
  [:exists [?e ::sub/request :part-item]]
  [[?e :part/mouse-down]]
  [?entity <- (acc/all)  :from [:e :all]]
  =>
  (trace "Inserting part response" ?e ?entity)
  (insert! [?e ::sub/response {:foo "bar"}]))

(def-tuple-rule acc-todos-that-are-visible
  [[?e :part/visible]]
  [?entity <- (acc/all) :from [?e :all]]
  =>
  ;; warning! this is bad!
  (trace "Inserting visible part" (mapv vals ?entity))
  (insert! [(guid) :visible-part ?entity]))

(def-tuple-rule subs-task-list
  [:exists [?e ::sub/request :task-list]]
  [?visible-todos <- (acc/all) :from [:visible-part]]
  [[_ :active-count ?active-count]]
  =>
  (let [res (map :v ?visible-todos)
        ents (map #(map util/record->vec %) res)
        ms (map util/tuple-entity->hash-map-entity ents)]))
    ;; FIXME. Ends up overwriting anything via notify! in store. May be problem with add
    ;; or remove changes method
    ;(insert!
    ;  [?e ::sub/response
    ;        {})]));:visible-todos ms
             ;:all-complete? (= ?active-count 0)})]))

(def-tuple-rule subs-part-app
  [:exists [?e ::sub/request :part-app]]
  [?todos <- (acc/all) :from [:part/title]]
  =>
  (trace "Inserting all-todos response" (mapv libx.util/record->vec ?todos))
  (insert! [?e ::sub/response "HI"]));(libx.util/tuples->maps (mapv libx.util/record->vec ?todos))]))

(def-tuple-rule subs-task-entry
  [:exists [?e ::sub/request :task-entry]]
  [[?eid :entry/title ?v]]
  =>
  (trace "[sub-response] Inserting new-part-title" ?v)
  (insert! [?e ::sub/response {:db/id ?eid :entry/title ?v}]))

;;TODO. Lib?
(def-tuple-rule entity-doesnt-exist-when-removal-requested
  [[_ :remove-entity-request ?eid]]
  [?entity <- (acc/all) :from [?eid :all]]
  =>
  (trace "Fulfilling remove entity request " ?entity)
  (doseq [tuple ?entity]
    (retract! tuple)))

;; TODO. Lib
(def-tuple-rule action-attr-cleanup
  {:group :cleanup}
  [[?actionId :action/type]]
  [?actionAttr <- [?actionId :all]]
  =>
  ;(trace "Removing action attr" ?actionAttr)
  (cr/retract! ?actionAttr))

(def-tuple-rule action-cleanup-last
                {:group :cleanup :salience -100}
  [?action <- [_ :action/type]]
  =>
  (trace "Removing action" ?action)
  (cr/retract! ?action))

(def groups [:action :calc :report :cleanup])
(def activation-group-fn (util/make-activation-group-fn :calc))
(def activation-group-sort-fn (util/make-activation-group-sort-fn groups :calc))
(def hierarchy (schema/schema->hierarchy app-schema))
(def ancestors-fn (util/make-ancestors-fn hierarchy))

;(def-tuple-session app-session
(cr/defsession app-session
  'story.rules
  :fact-type-fn :a
  :ancestors-fn ancestors-fn
  :activation-group-fn activation-group-fn
  :activation-group-sort-fn activation-group-sort-fn)
