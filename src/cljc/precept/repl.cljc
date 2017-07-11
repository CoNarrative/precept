(ns precept.repl
    (:require [precept.state :as state]
              [precept.listeners :as l]
              [precept.util :as util]
              [precept.rules :refer [fire-rules] :as rules]
      #?(:clj [clara.rules.compiler :as com])
      #?(:clj [cljs.env :as env])
      #?(:clj [cljs.analyzer.api :as ana-api])
      #?(:clj [cljs.analyzer :as ana])
      #?(:clj [clara.macros :as cm]))
  #?(:cljs (:require-macros [precept.repl])))

#?(:clj
    (defn redef-session! [sess]
      (let [session-name (symbol (name sess))
            session-def (get @state/session-defs session-name)
            session-var (get (ns-interns (:ns-name session-def)) session-name)]
        (alter-var-root session-var (fn [_] (com/mk-session (:body session-def)))))))

#?(:clj
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
             :fact-id @state/fact-id}))))

#?(:clj
    (defn unmap-all-rules! [sess]
      (let [nses (get-in @state/session-defs [(symbol (name sess)) :rule-nses])
            source-nses (remove #(= % (symbol (name 'precept.impl.rules))) nses)]
        (doseq [rule-ns source-nses]
          (doseq [[k v] (ns-interns rule-ns)]
            (let [registered-rules (util/rules-in-ns (ns-name rule-ns))]
              (when (contains? registered-rules k)
                (do (ns-unmap rule-ns k)
                    (swap! state/rules util/dissoc-in [k])
                    (swap! state/rule-files conj (:file (meta v)))))))))))

#?(:clj
    (defn reload-session!
      [sess]
      "REPL utility function to reload a session.
      Clears all interned rules from nses and loads files containing those namespaces to ensure
      rules are in sync. Redefines the session with the arguments it was created with.
      Synchronizes session state by re-inserting all unconditinal inserts and firing rules."
      (do
        (unmap-all-rules! sess)
        (doseq [filename @state/rule-files]
          ;(println "Loading rule file..." filename)
          (load-file filename))
        (redef-session! sess)
        (recreate-session-state! sess))))

;; CLJS
;; We're trying to do most of the work at compile time, so even
;; though the reader conditionals are CLJ, the underlying functionality
;; is for CLJS

#?(:clj
   (defn all-compiled-rules [cenv]
     (get @cenv :clara.macros/productions)))

#?(:clj
    (defn clear-compiled-rules! [compiler-env]
      (swap! compiler-env dissoc :clara.macros/productions)))

#?(:clj
    (defn compiled-rule-names [compiled-rules]
      (keys (first (vals compiled-rules)))))

#?(:clj
    (defn without-impl-rules [compiled-rules]
      (into {}
        (remove (fn [[k v]] (= k (symbol (name 'precept.impl.rules))))
          compiled-rules))))

;; Unmapping rules - WIP
(comment
  (defn unmap-rule-in-ns [[rule-sym rule-var] rule-ns]
    (let [registered-rules (util/rules-in-ns (ns-name rule-ns))]
      (when (contains? registered-rules rule-sym)
        `(do (cljs.core/ns-unmap ~rule-ns ~rule-sym)
             (swap! precept.state/rules precept.util/dissoc-in [~rule-sym])))))

  (defn unmap-all-rules-cljs! [sess session-defs]
    (let [nses (get-in session-defs [(symbol (name sess)) :rule-nses])
          source-nses (remove #(= % (symbol (name 'precept.impl.rules)))
                         nses)])))


;; TODO. - [x] Find rule nses
;; TODO. - [ ] precept.impl.rules not showing up as NS or rule defs
;; TODO. - [ ] ns-unmap rules from namespace (compile time)
;; TODO. - [x] Return session def to calling namespace
;; TODO. - [x] Research mount/defstate
;; TODO. - [x] Insert facts after RT redeffed session var to CLJS ns
;; TODO. - [x] Get uncond inserts from runtime
;; TODO. - [ ] Clear uncond inserts after inserted into redeffed session
;; TODO. - [ ] Clear fact-index
;; TODO. - [x] Clear productions from compiler env
;; TODO. - [x] Clear state/rules, let reregister on recompile
;; TODO - [ ] Determine whether session registry CLJS should be accessible at compile time
;; instead of runtime only so that we can pass the same arguments to (session)
#?(:clj
   (defmacro reload-session-cljs! [sess]
     (let [_ (println "I am Clojure" (java.util.UUID/randomUUID))
           compiled-rules (all-compiled-rules env/*compiler*)
           non-impl-rules (without-impl-rules compiled-rules)
           rule-names (compiled-rule-names non-impl-rules)
           rule-nses (keys non-impl-rules)
           interns (mapv (comp keys ana-api/ns-interns) rule-nses)
           _ (println "Session " sess)
           [quot session-name] sess
           _ (println "Compiled rules" compiled-rules)
           _ (println "Compiled rule names" rule-names)
           _ (println "Rule nses" rule-nses)
           _ (println "Non-impl rules" non-impl-rules)
           _ (println "Interns" interns)
           _ (println "Session not-a-var"
               (ana-api/resolve @env/*compiler* 'precept.app-ns/my-session))
           removed-compiled-rules (clear-compiled-rules! env/*compiler*)
           uncond-inserts (ana-api/resolve @env/*compiler* 'precept.state/unconditional-inserts)]
       `(let [nses# (get-in @precept.state/session-defs
                           [(symbol (name ~sess)) :rule-nses])
              source-nses# (remove #(= % (symbol (name 'precept.impl.rules))) nses#)
              quoted-nses# (mapv (fn [x#] `'~x#) source-nses#)
              _# (.log js/console "I am Javascript")]
         (do
           (precept.macros/session ~session-name 'precept.app-ns)
           (-> ~session-name
             (precept.util/insert [:some :fact "here"])
             (precept.rules/fire-rules))
           (ns-interns 'precept.app-ns))))))

             ;(comment
             ;  (doseq [rule-ns# source-nses#]
             ;    ;; runtime compilation-time mismatch here. Might unstick
             ;    ;; if we just store the current runtime data in he compiler
             ;    ;; so we can access it with ana/ns-interns, get the keys that are symbols
             ;    ;; and try to quote them from there...?
             ;    (doseq [[k v] (ns-interns rule-ns#)]
             ;      (let [registered-rules (util/rules-in-ns (ns-name rule-ns#))]
             ;        (when (contains? registered-rules k)
             ;          (do (ns-unmap rule-ns# k)
             ;              (swap! state/rules util/dissoc-in [k])
             ;              (swap! state/rule-files conj (:file (meta v))))))))))))))
