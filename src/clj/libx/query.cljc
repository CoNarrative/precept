(ns libx.query
  (:require [clara.rules :as cr]
            [libx.util :as util]))

(cr/defquery qav-
  "(Q)uery (A)ttribute (V)alue.
  Finds facts matching args attribute and value"
  [:?a :?v]
  [:all (= (:e this) ?e) (= (:a this) ?a) (= (:v this) ?v)])

(defn qav [session a v]
  (cr/query session qav- :?a a :?v v))

;(cr/defquery qave-
;  "(Q)uery (A)ttribute (V)alue (E)ntity.
;  Finds facts matching args attribute, value and eid"
;  [:?a :?v :?e]
;  [:all (= (:e this) ?e) (= (:a this) ?a) (= (:v this) ?v)])

;(defn qave [session a v e]
;  (cr/query session qav- :?a a :?v v :?e e))

; TODO. This doesn't return the whole entity, just tuples matching eid.
; Entity is constructed by other helpers
; When added perf test jumps to ~3800ms, ~12ms per iteration after initial
(cr/defquery entity-
  [:?e]
  [?entity <- :all (= ?e (:e this))])

(defn entityv
  [session e]
  (mapv (comp util/record->vec :?entity)
    (cr/query session entity- :?e e)))

(defn entity
  [session e]
  (util/entity-tuples->entity-map
    (entityv session e)))

(cr/defquery qa-
  [:?a]
  [:all (= (:e this) ?e) (= (:a this) ?a) (= (:v this) ?v)])

(defn qa [session a]
  (cr/query session qa- :?a a))

;(cr/defquery qe
; [:?e]
; [:all (= (:e this) ?e) (= (:a this) ?a) (= (:v this) ?v)])

(defn entities-where
  "Returns hydrated entities matching an attribute-only or an attribute-value query"
  ([session a] (map #(entity session (:db/id %)) (util/clara-tups->maps (qa session a))))
  ([session a v] (map #(entity session (:db/id %)) (util/clara-tups->maps (qav session a v)))))
  ;([session a v e] (map #(entity session (:db/id %)) (util/clara-tups->maps (qave session a v e)))))

(defn facts-where
  "Returns tuples matching a v e query where v, e optional"
  ([session a] (mapv util/keyed-tup->vector-tup (qa session a)))
  ([session a v] (mapv util/keyed-tup->vector-tup (qav session a v))))
  ;([session a v e] (mapv util/keyed-tup->vector-tup (qave session a v e))))

