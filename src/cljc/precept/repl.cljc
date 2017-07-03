(ns precept.repl
  (:require [precept.state :as state]
            [precept.listeners :as l]
            [precept.util :as util]
            [precept.rules :refer [fire-rules]]
            [clara.rules.compiler :as com]))

(defn redef-session! [sess]
  (let [session-name (symbol (name sess))
        session-def (get @state/session-defs session-name)
        session-var (get (ns-interns (:ns-name session-def)) session-name)]
    (alter-var-root session-var (fn [_] (com/mk-session (:body session-def))))))

(defn recreate-session-state! [sess]
  (let [session-name (symbol (name sess))
        session-def (get @state/session-defs session-name)
        session-var (get (ns-interns (:ns-name session-def)) session-name)
        unconditional-insert-history (vec @state/unconditional-inserts) ;; Note: we don't know the
        ;; specific session these uncond inserts belong to at the moment
        max-fact-id (apply max (map :t unconditional-insert-history))]
    (do (reset! state/fact-index {})
        (reset! state/fact-id max-fact-id)
        {:session-before (var-get session-var)
         :session-after (-> (var-get session-var)
                          (l/replace-listener)
                          (util/insert unconditional-insert-history)
                          (fire-rules))
         :facts unconditional-insert-history
         :fact-id @state/fact-id})))

(defn unmap-all-rules! [sess]
  (let [nses (get-in @state/session-defs [(symbol (name sess)) :rule-nses])
        source-nses (remove #(= % (symbol (name 'precept.impl.rules))) nses)]
    (doseq [rule-ns source-nses]
      (doseq [[k v] (ns-interns rule-ns)]
        (let [registered-rules (util/rules-in-ns (ns-name rule-ns))]
          (when (contains? registered-rules k)
            (do (ns-unmap rule-ns k)
                (swap! state/rules util/dissoc-in [k])
                (swap! state/rule-files conj (:file (meta v))))))))))

(defn reload-session! [sess]
  (do
    (unmap-all-rules! sess)
    (doseq [filename @state/rule-files]
      (println "Loading rule file..." filename)
      (load-file filename))
    (redef-session! sess)
    (recreate-session-state! sess)))

