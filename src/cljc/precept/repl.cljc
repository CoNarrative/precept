(ns precept.repl
    (:require [precept.state :as state]
              [precept.listeners :as l]
              [precept.util :as util]
              [precept.rules :refer [fire-rules] :as rules]
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

(defn impl-rule? [m]
  (cond
    (not (contains? m :ns-name)) false ;; until cr queries have ns name
    (= (:ns-name m) '(quote precept.impl.rules)) true))

(defn without-impl-rules
  [compiled-rules]
  (reduce
    (fn [acc [rule-ns m]]
      (let [v (reduce
                (fn [acc2 [rule-name m2]]
                  (if (impl-rule? m2)
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
   (defn remove-stale-productions! [cenv stale-productions]
     (doseq [[rule-ns rule-name] stale-productions]
       (remove-production! cenv rule-ns rule-name))))

#?(:clj
    (defmacro remove-stale-runtime-rule-defs! [rule-defs stale-productions]
      (let [quoted-productions (mapv (fn [[l r]] (vector `'~l `'~r)) stale-productions)]
        `(doseq [[rule-ns# rule-name#] ~quoted-productions]
           (swap! precept.state/rules precept.util/dissoc-in [rule-name#])))))

(defn ns-name-intern-pairs [rule-ns]
  (let [syms (keys (ana-api/ns-interns rule-ns))]
    (->> syms
      (interleave (repeat (count syms) rule-ns))
      (partition 2)
      (map vec))))

;; TODO. - [ ] Programmatically force reload of session's namespace https://github.com/bhauman/lein-figwheel/issues/341
;; TODO. - [x] Reset state/fact-id to max-fact-id
;; TODO. - [x] Sync runtime rule defs
;; TODO. - [x] Unmap old session ~~or silence Google Closure :duplicate-vars warning~~ so Figwheel
;; can compile when session is deffed in file and macro is invoked
;; https://clojurescript.org/reference/compiler-options#warnings
;; Note: Added ns-unmap in CLJS for session, warning still generated sometimes. Verify unmap
;; is actually occurring in these instances
;; TODO. - [x] Remove non-interned productions from compiler env
;; TODO. - [x] Get body from session def, pass as map arg to session for redef
;; TODO. - [x] Insert uncond inserts (cur. set up for compile time, but may be possible
;; at macroexpand/runtime)
;; TODO. - [x] Clear uncond inserts after inserted into redeffed session
;; TODO. - [x] Clear fact-index
;; TODO. - [x] Find rule nses
;; TODO. - [x] precept.impl.rules not showing up as NS or rule defs
;; TODO. - [x] Return session def to calling namespace
;; TODO. - [x] Research mount/defstate
;; TODO. - [x] Insert facts after RT redeffed session var to CLJS ns
;; TODO. - [x] Get uncond inserts from runtime
;; TODO. - [x] Clear productions from compiler env
;; TODO. - [x] Clear state/rules, let reregister on recompile
;; TODO. - [x] Determine whether session registry CLJS should be accessible at compile time
;; instead of runtime only so that we can pass the same arguments to (session)
#?(:clj
   (defmacro reload-session-cljs! [sess]
     (let [compiled-rules (all-compiled-rules env/*compiler*)
           non-impl-rules (without-impl-rules compiled-rules)
           rule-nses (vec (set (map first non-impl-rules)))
           interns (mapcat #(ns-name-intern-pairs %) rule-nses)
           stale-productions (clojure.set/difference (set non-impl-rules) (set interns))
           [quot session-name] sess
           session-defs (get @env/*compiler* :precept.macros/session-defs)
           session-def (first (filter #(= session-name (:name %)) session-defs))]
       (remove-stale-productions! env/*compiler* stale-productions)
       (build-api/mark-cljs-ns-for-recompile! (:ns-name session-def))
       `(let [uncond-inserts# (vec @precept.state/unconditional-inserts)
              max-fact-id# (apply max (map :t uncond-inserts#))]
         (do
           (cljs.core/ns-unmap '~(:name session-def) '~(:ns-name session-def))
           (remove-stale-runtime-rule-defs! ~'precept.state/rules ~stale-productions)
           (reset! precept.state/unconditional-inserts #{})
           (reset! precept.state/fact-index {})
           (reset! precept.state/fact-id max-fact-id#)
           (precept.macros/session ~session-def)
           (-> ~session-name
             (precept.listeners/replace-listener)
             (precept.util/insert uncond-inserts#)
             (precept.rules/fire-rules)))))))
