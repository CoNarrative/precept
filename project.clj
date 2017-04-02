(defproject libx "0.0.3"
  :url          "https://github.com/CoNarrative/libx.git"
  :license      {:name "Eclipse Public License The Same As Clojure"
                 :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.9.0-alpha15"]
                 [org.clojure/clojurescript "1.9.494"]
                 [com.cerner/clara-rules "0.14.0"]]

  :source-paths ["src/clj" "dev"]

  :profiles
  {:dev
   {:dependencies [[org.clojure/test.check "0.9.0"]]}})
