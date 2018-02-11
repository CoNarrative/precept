(ns precept.serialize.dto
  (:require [precept.util :as util]))

(defn sub-registration? [facts]
  (and (= (count facts) 1)
    (= (:a (first facts))
      :precept.spec.sub/request)))

(defn untracked-impl-rule?
  [ns-name name]
  (and (= 'precept.impl.rules ns-name)
    (not= name "clean-transients___impl")))


(defn define-name? [name lhs]
  ;;TODO. This isn't good enough--user-generated rule names could include "define-".
  ;; Attempted to recreate the name from the LHS arg, but the encoding is different
  ;; than what we use to generate the hash for the name since it comes from clara
  ;(= name (str "define-" (hash lhs))))
  (clojure.string/includes? name "define-"))

(defn name-or-lhs-str [name lhs]
  (if (define-name? name lhs) (str lhs) name))

(defn without-namespace [s]
  (second (clojure.string/split s #"/")))
(defn get-rule-display-name [name lhs]
  (-> (without-namespace name)
    (name-or-lhs-str lhs)))

;; TODO. This is a bit wacky...investigate how we're handling serialization encoding and
;; see if it can be done in one place. These methods return EDN with either string keys
;; or kw keys and that's it. Might actually be more efficient to do it this way because we're
;; not converting kws to string keys in case of json encoding but it's a bit confusing
;; and doesn't convert kw keys of the value fields (best done during serialization, if we want
;; to do that at all)
(defmulti rule-event-dto :encoding)

(defmethod rule-event-dto :json
  [{:keys [type event-number state-number state-id display-name name ns-name lhs rhs props
           matches bindings facts]}]
  {"id" (util/guid)
   "type" type
   "eventNumber" event-number
   "stateNumber" state-number
   "stateId" state-id
   "displayName" display-name
   "name" name
   "nsName" ns-name
   "lhs" lhs
   "rhs" rhs
   "props" props
   "matches" matches
   "bindings" bindings
   "facts" facts})

(defmethod rule-event-dto :default [m]
  (dissoc m :encoding))

(defmulti action-dto :encoding)

(defmethod action-dto :json [{:keys [type action? facts event-number state-number state-id]}]
  {"id" (util/guid)
   "type" type
   "action" action?
   "facts" facts
   "eventNumber" event-number
   "stateNumber" state-number
   "stateId" state-id})

(defmethod action-dto :default [m]
  (dissoc m :encoding))

;; TODO. Allow configure encoding for "JSON" vs. "EDN" (string keys vs. keyword keys)
(defn event-dto
  "Data transfer object.
  - `event` - keyword. One of `#{:add-facts :add-facts-logical :retract-facts
                                 :retract-facts-logical}`
  - node - hash-map (nilable).
  - token - hash-map (nilable).
  - facts - vector (nilable).
  - *event-coords - atom. Contains current state number, event number.
  - encoding - keyword. Any valid transit encoding."
  ([event node token facts *event-coords]
   (event-dto event node token facts *event-coords :edn))
  ([event node token facts *event-coords encoding]
   (let [{:keys [event-number state-number state-id]} @*event-coords
         non-rulegen-facts (util/remove-rulegen-facts facts)]
     (cond
       (sub-registration? facts)
       {:id (util/guid)
        :type event
        :facts (util/record->map facts)
        :state-number state-number
        :event-number event-number
        :state-id state-id
        :encoding encoding}

       (= 0 (count non-rulegen-facts))
       {:id (util/guid)
        :state-number state-number
        :event-number event-number
        :state-id state-id
        :encoding encoding
        :impl? true}

       (and (= nil node token) (= event-number 0))
       (action-dto {:id (util/guid)
                    :type event
                    :action true
                    :facts (util/record->map facts)
                    :event-number event-number
                    :state-number state-number
                    :state-id state-id
                    :encoding encoding})

       ;; TODO. May not be getting node, token from :retract-facts
       (= nil node token)
       (action-dto {:id (util/guid)
                    :type event
                    :action true
                    :facts (util/record->map facts)
                    :event-number event-number
                    :state-number state-number
                    :state-id state-id
                    :encoding encoding})

       :default
       (let [rule (:production node)
             {:keys [ns-name lhs rhs props name]} rule
             {:keys [matches bindings]} token
             display-name (get-rule-display-name name lhs)]
         (if (untracked-impl-rule? ns-name display-name)
           (let [{:keys [event-number state-number state-id]} @*event-coords]
             {:impl? true})
           (let [{:keys [event-number state-number state-id]} @*event-coords]
             (rule-event-dto {:id (util/guid)
                              :type event
                              :event-number event-number
                              :state-number state-number
                              :state-id state-id
                              :display-name display-name
                              :name name
                              :ns-name ns-name
                              :lhs lhs
                              :rhs rhs
                              :props props
                              :matches (util/record->map matches)
                              :bindings (util/record->map bindings)
                              :facts (util/record->map facts)
                              :encoding encoding}))))))))


