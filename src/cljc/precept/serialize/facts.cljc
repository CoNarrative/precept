(ns precept.serialize.facts
  (:require [cognitect.transit :as t])
  #?(:clj (:import [java.io ByteArrayOutputStream])))


#?(:cljs
   (defn fn->map [v]
     (let [xs (clojure.string/split (.-name v) #"\$")
           name (last xs)
           ns (clojure.string/join "." (butlast xs))]
       {:name (str ns "/" name)
        :ns ns
        :display-name name
        :type "js/Function"
        :fn? true
        :string (.toString v)})))

#?(:cljs
    (deftype FunctionHandler []
      Object
      (tag [this v] "map")
      (rep [this v] (fn->map v))
      (stringRep [this v])))

#?(:cljs
   (defn serialize [x]
     (let [writer (t/writer :json-verbose
                    {:handlers {js/Function (FunctionHandler.)}})]
       (t/write writer x))))

;; TODO. Extract better data from clojure.lang.IFns
#?(:clj
   (defn fn->map [v]
     (let [xs (clojure.string/split (str (type v)) #"\$")
           name (last xs)
           ns (clojure.string/join "." (butlast xs))]
       {:name (str ns "/" name)
        :ns ns
        :display-name name
        :type "js/Function"
        :fn? true
        :string (.toString v)})))

#?(:clj
   (defn function-handler []
     (t/write-handler
       (fn [v] "map")
       (fn [v] (fn->map v))
       (fn [v]))))

#?(:clj
   (defn serialize [x]
     (let [out (ByteArrayOutputStream. 4096)
           writer (t/writer
                    out
                    :json-verbose
                    {:handlers {clojure.lang.IFn (function-handler)}})]
       (t/write writer x))))
