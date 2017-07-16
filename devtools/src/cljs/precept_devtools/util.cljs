(ns precept-devtools.util)


(defmulti display-text :type)

(defmethod display-text "define" [m]
  (-> (update m :display-name #(str (vec (rest (first (:rhs m))))))
    (update :lhs str)))

(defmethod display-text :default [m]
  (-> (update m :display-name #(str (:name m)))
    (update :lhs str)))

(defn impl-rule? [m]
  (clojure.string/includes? (str (:name m)) "___impl"))

;; Borrowed from figwheel
(defn node [t attrs & children]
  (let [e (.createElement js/document (name t))]
    (doseq [k (keys attrs)] (.setAttribute e (name k) (get attrs k)))
    (doseq [ch children] (.appendChild e ch)) ;; children
    e))

(defn get-or-create-mount-node! [mount-node-id]
  (if-not (.getElementById js/document mount-node-id)
    (let [mount-node (node :div
                       {:id mount-node-id
                        :style
                            (str "-webkit-transition: all 0.2s ease-in-out;"
                              "-moz-transition: all 0.2s ease-in-out;"
                              "-o-transition: all 0.2s ease-in-out;"
                              "transition: all 0.2s ease-in-out;"
                              "font-size: 13px;"
                              "border-top: 1px solid #f5f5f5;"
                              "box-shadow: 0px 0px 1px #aaaaaa;"
                              "line-height: 18px;"
                              "color: #333;"
                              "font-family: monospace;"
                              "padding: 0px 10px 0px 70px;"
                              "position: fixed;"
                              "bottom: 0px;"
                              "left: 0px;"
                              "height: 100%;"
                              "width: 60%;"
                              "opacity: 1.0;"
                              "overflow: scroll;"
                              "background: aliceblue;"
                              "box-sizing: border-box;"
                              "z-index: 9999;"
                              "text-align: left;")})]
      (do (-> (.-body js/document) (.appendChild mount-node))
          (.getElementById js/document mount-node-id)))
    (.getElementById js/document mount-node-id)))
