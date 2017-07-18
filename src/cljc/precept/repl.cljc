(ns precept.repl
    (:require [precept.state :as state]
              [precept.listeners :as l]
              [precept.util :as util]
              ;; Avoiding circular dependency on precept.rules. Will become an issue if our fire
              ;; rules becomes different
              [clara.rules :refer [fire-rules]]
      #?(:clj [clara.rules.compiler :as com])
      #?(:clj [cljs.env :as env])
      #?(:clj [cljs.analyzer.api :as ana-api])
      #?(:clj [cljs.analyzer :as ana])
      #?(:clj [cljs.build.api :as build-api])
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
      Synchronizes session state by re-inserting all unconditional inserts and firing rules."
      (do
        (unmap-all-rules! sess)
        (doseq [filename @state/rule-files]
          ;(println "Loading rule file..." filename)
          (load-file filename))
        (redef-session! sess)
        (recreate-session-state! sess))))

;; CLJS
;; We're doing a lot at compile time; even though the reader conditionals are CLJ,
;; the following is for CLJS exclusively

#?(:clj
   (defn all-compiled-rules [cenv]
     (get @cenv :clara.macros/productions)))

(defn impl-namespace? [m]
  (cond
    (not (contains? m :ns-name)) false ;; until cr queries have ns name
    (= (:ns-name m) '(quote precept.impl.rules)) true))

(defn without-impl-rules
  [compiled-rules]
  (reduce
    (fn [acc [rule-ns m]]
      (let [v (reduce
                (fn [acc2 [rule-name m2]]
                  (if (impl-namespace? m2)
                    acc2
                    (conj acc2 [rule-ns rule-name])))
                []
                m)]
        (concat acc v)))
    []
    compiled-rules))

#?(:clj
   (defn remove-production! [cenv rule-ns rule-name]
     (swap! cenv util/dissoc-in [:clara.macros/productions rule-ns rule-name])))

#?(:clj
   (defn remove-stale-productions!
     [cenv stale-productions]
     "Takes a set of stale-productions - rule definitions determined to exist in cljs
     .env/*compiler* but not as an intern in the namespace. Removes these from the path in cljs
     .env/*compiler* where Clara obtains rule sources from when building a session in cljs"
     (doseq [[rule-ns rule-name] stale-productions]
       (remove-production! cenv rule-ns rule-name))))

#?(:clj
    (defmacro remove-stale-runtime-rule-defs!
      "Synchronizes runtime rule definitions in precept.state/rules with rules interned
      in namespace. stale-productions are a set of rule names that were in the compiler env but
      not interned in the namespace"
      [stale-productions]
      (let [quoted-productions (mapv (fn [[l r]] (vector `'~l `'~r)) stale-productions)]
        `(doseq [[rule-ns# rule-name#] ~quoted-productions]
           (swap! precept.state/rules precept.util/dissoc-in [rule-name#])))))

(defn ns-name-intern-pairs
  "Returns [[ns-name ns-intern]...] for ease of diffing productions in compiler env and productions
  interned in rule-ns. Obtains ns-interns from cljs.analyzer"
  [rule-ns]
  (let [syms (keys (ana-api/ns-interns rule-ns))]
    (->> syms
      (interleave (repeat (count syms) rule-ns))
      (partition 2)
      (map vec))))

#?(:clj
   (defmacro reload-session-cljs!
     [sess]
     "Reloads session's rules and facts in CLJS."
     (let [compiled-rules (all-compiled-rules env/*compiler*)
           non-impl-rules (without-impl-rules compiled-rules)
           rule-nses (vec (set (map first non-impl-rules)))
           interns (mapcat #(ns-name-intern-pairs %) rule-nses)
           stale-productions (clojure.set/difference (set non-impl-rules) (set interns))
           [quot session-name] sess
           session-defs (get @env/*compiler* :precept.macros/session-defs)
           session-def (first (filter #(= session-name (:name %)) session-defs))]
       (remove-stale-productions! env/*compiler* stale-productions)
       ;(build-api/mark-cljs-ns-for-recompile! (:ns-name session-def))
       `(let [uncond-inserts# (vec @precept.state/unconditional-inserts)
              max-fact-id# (if (empty? uncond-inserts#) -1 (apply max (map :t uncond-inserts#)))
              session-name# '~(:name session-def)
              session-name2# '~(:name session-def)
              session-ns# '~(:ns-name session-def)]
         (do
           (cljs.core/ns-unmap '~(:ns-name session-def) '~(:name session-def))
           (remove-stale-runtime-rule-defs! ~stale-productions)
           (reset! precept.state/unconditional-inserts #{})
           (reset! precept.state/fact-index {})
           (reset! precept.state/fact-id max-fact-id#)
           (precept.macros/session* ~session-def)
           (-> ~session-name
             (precept.listeners/replace-listener)
             (precept.util/insert uncond-inserts#)
             (clara.rules/fire-rules)))))))

#?(:clj
   (defmacro redef-session-cljs!
     [sess]
     "- `sess` - quoted symbol (name of session)

     Reloads session's rules and facts in CLJS and returns a def.
     Overwrites existing session definition when the file is reloaded using the same semantics as
    `reload-session-cljs!`. Eliminates the need for an explicit REPL call to `reload-session-cljs!`
     when commenting out, renaming, or removing rules.

     Usage:
     ```clj
     (session 'my-session <options>)
     (redef-session-cljs! 'my-session)
     ```

     Make changes to rules, save file.

     **IMPORTANT**: If using figwheel, ensure `:load-warninged-code` option is set to `true`.
     This macro redefines the session programmatically by adding a new def to the session's
     namespace. In the future this requirement may be removed (`session` may return the reloaded
     definition automatically if a `:reload true` argument is provided).
     "
     (let [[quot session-name] sess]
       `(def ~session-name (reload-session-cljs! ~sess)))))
