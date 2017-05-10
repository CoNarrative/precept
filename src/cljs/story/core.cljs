(ns ^:figwheel-always story.core
  (:require [impi.core :as impi]
            [libx.core :refer [start! then then-tuples]]
            [story.schema :refer [app-schema]]
            [story.rules :refer [app-session]]
            [devtools.core :as devtools]))

(enable-console-print!)
(devtools/install!)

(def outline-shader
  "precision mediump float;

   varying vec2 vTextureCoord;
   uniform sampler2D uSampler;
   uniform vec2 dimensions;

   void main(void) {
       vec2 pixelSize  = vec2(1.0) / dimensions;
       vec4 pixel      = texture2D(uSampler, vTextureCoord);
       vec4 pixelUp    = texture2D(uSampler, vTextureCoord - vec2(0.0, pixelSize.y));
       vec4 pixelDown  = texture2D(uSampler, vTextureCoord + vec2(0.0, pixelSize.y));
       vec4 pixelLeft  = texture2D(uSampler, vTextureCoord - vec2(pixelSize.x, 0.0));
       vec4 pixelRight = texture2D(uSampler, vTextureCoord + vec2(pixelSize.x, 0.0));

       if (pixel.a == 0.0 && (pixelUp.a    > 0.0 ||
                              pixelDown.a  > 0.0 ||
                              pixelLeft.a  > 0.0 ||
                              pixelRight.a > 0.0)) {
           pixel = vec4(1.0, 0.0, 0.0, 1.0);
       }
       else {
           pixel = vec4(0.0, 0.0, 0.0, 0.0);
       }

       gl_FragColor = pixel;
   }")

(defonce state (atom {}))

(reset!
  state
  {:pixi/renderer
   {:pixi.renderer/size             [400 400]
    :pixi.renderer/background-color 0xbbbbbb
    :pixi.renderer/transparent?     false}
   :pixi/listeners
   {:click      (fn [_ id] (prn :click id))
    :mouse-down (fn [_] (prn :mouse-down))
    :mouse-up   (fn [_] (prn :mouse-up))
    :mouse-over (fn [_] (prn :mouse-over))
    :mouse-out  (fn [_] (prn :mouse-out))}
   :pixi/stage
   {:impi/key         :stage
    :pixi.object/type :pixi.object.type/container
    :pixi.container/children
                      (sorted-map
                        :a
                        {:impi/key :background
                         :pixi.object/type :pixi.object.type/container
                         :pixi.object/interactive? true
                         :pixi.object/contains-point (constantly true)
                         :pixi.event/click [:click :background]}
                        :b
                        {:impi/key :performance
                         :pixi.object/type :pixi.object.type/container
                         :pixi.container/children
                         (vec (for [i (range 5), j (range 5)]
                                {:impi/key             (keyword (str "bunny" i "_" j))
                                 :pixi.object/type     :pixi.object.type/sprite
                                 :pixi.object/position [(+ 200 (* 30 i)) (+ 40 (* 40 j))]
                                 :pixi.object/rotation 0.0
                                 :pixi.sprite/anchor   [0.5 0.5]
                                 :pixi.sprite/texture  {:pixi.texture/source "img/bunny.png"}}))}
                        :c
                        {:impi/key                 :bunny2
                         :pixi.object/type         :pixi.object.type/sprite
                         :pixi.object/position     [100 100]
                         :pixi.object/scale        [5 5]
                         :pixi.object/interactive? true
                         :pixi.event/click         [:click :bunny2]
                         :pixi.event/mouse-down    [:mouse-down]
                         :pixi.event/mouse-up      [:mouse-up]
                         :pixi.event/mouse-over    [:mouse-over]
                         :pixi.event/mouse-out     [:mouse-out]
                         :pixi.sprite/anchor       [0.5 0.5]
                         :pixi.sprite/texture
                                                   {:pixi.texture/scale-mode :pixi.texture.scale-mode/nearest
                                                    :pixi.texture/source     "img/bunny.png"}}
                        :d
                        {:impi/key             :rendered
                         :pixi.object/type     :pixi.object.type/sprite
                         :pixi.object/position [0 0]
                         :pixi.object/scale    [3 3]
                         :pixi.object/alpha    0.8
                         :pixi.sprite/texture
                                               {:pixi.texture/scale-mode :pixi.texture.scale-mode/nearest
                                                :pixi.render-texture/size [100 100]
                                                :pixi.render-texture/source
                                                {:impi/key             :bunny3
                                                 :pixi.object/type     :pixi.object.type/sprite
                                                 :pixi.object/position [50 50]
                                                 :pixi.sprite/anchor   [0.5 0.5]
                                                 :pixi.sprite/texture
                                                                       {:pixi.texture/scale-mode :pixi.texture.scale-mode/nearest
                                                                        :pixi.texture/source     "img/bunny.png"}
                                                 :pixi.object/filters
                                                                       [{:pixi.filter/fragment outline-shader
                                                                         :pixi.filter/uniforms {:dimensions {:type "2f" :value [400.0 300.0]}}}]}}}
                        :e
                        {:impi/key :gfx
                         :pixi.object/position [50 240]
                         :pixi.object/type :pixi.object.type/graphics
                         :pixi.graphics/shapes
                         [{:pixi.shape/type :pixi.shape.type/circle
                           :pixi.shape/position [0 20]
                           :pixi.circle/radius 20
                           :pixi.shape/line
                           {:pixi.line/width 4
                            :pixi.line/color 0x22FF11
                            :pixi.line/alpha 0.7}}
                          {:pixi.shape/type :pixi.shape.type/ellipse
                           :pixi.shape/position [60 20]
                           :pixi.ellipse/radius [30 20]
                           :pixi.shape/fill {:pixi.fill/color 0xFF0000}}
                          {:pixi.shape/type :pixi.shape.type/polygon
                           :pixi.polygon/path [100 0, 160 0, 130 40, 100 40]
                           :pixi.shape/fill {:pixi.fill/color 0xFFFF00}}
                          {:pixi.shape/type :pixi.shape.type/rectangle
                           :pixi.shape/position [170 0]
                           :pixi.shape/size [50 40]
                           :pixi.shape/fill {:pixi.fill/color 0x004433
                                             :pixi.fill/alpha 0.6}}
                          {:pixi.shape/type :pixi.shape.type/rounded-rectangle
                           :pixi.shape/position [240 0]
                           :pixi.shape/size [50 40]
                           :pixi.rounded-rectangle/radius 5
                           :pixi.shape/fill {:pixi.fill/color 0x221155}}]}
                        :f
                        {:impi/key :text
                         :pixi.object/type :pixi.object.type/text
                         :pixi.object/position [100 290]
                         :pixi.text/text "Hello World!"
                         :pixi.text/style
                         {:pixi.text.style/align "center"
                          :pixi.text.style/break-words true
                          :pixi.text.style/drop-shadow true
                          :pixi.text.style/drop-shadow-angle (/ Math.PI 6)
                          :pixi.text.style/drop-shadow-blur 0
                          :pixi.text.style/drop-shadow-color 0x222222
                          :pixi.text.style/drop-shadow-distance 5
                          :pixi.text.style/fill 0xFFFF00
                          :pixi.text.style/font-family "Arial"
                          :pixi.text.style/font-size 24
                          :pixi.text.style/font-style "italic"
                          :pixi.text.style/font-variant "small-caps"
                          :pixi.text.style/font-weight "bold"
                          :pixi.text.style/letter-spacing 0
                          :pixi.text.style/line-height 26
                          :pixi.text.style/line-join "miter"
                          :pixi.text.style/miter-limit 10
                          :pixi.text.style/padding 0
                          :pixi.text.style/stroke 0x00FFFF
                          :pixi.text.style/stroke-thickness 0
                          :pixi.text.style/word-wrap true
                          :pixi.text.style/word-wrap-width 100}}
                        :g
                        {:impi/key                :animated
                         :pixi.object/type        :pixi.object.type/movie-clip
                         :pixi.object/position    [50 320]
                         :pixi.object/scale       [2 2]
                         :pixi.sprite/anchor      [0.5 0.5]
                         :pixi.movie-clip/loop?   true
                         :pixi.movie-clip/paused? false
                         :pixi.movie-clip/animation-speed 0.5
                         :pixi.movie-clip/frames
                         [{:pixi.frame/duration 1000
                           :pixi.frame/texture  {:pixi.texture/scale-mode :pixi.texture.scale-mode/nearest
                                                 :pixi.texture/source "img/bunny.png"}}
                          {:pixi.frame/duration 250
                           :pixi.frame/texture  {:pixi.texture/scale-mode :pixi.texture.scale-mode/nearest
                                                 :pixi.texture/source "img/bunnyblink.png"}}]})}})

(defn- rotate-children [children]
  (for [child children]
    (update child :pixi.object/rotation + 0.1)))

(defn animate [state]
  (swap! state
         update-in [:pixi/stage :pixi.container/children :b :pixi.container/children]
         rotate-children)
  (js/setTimeout #(animate state) 16))

(def facts (concat
             [[0 :mouse/op-mode :at-rest]]))

(defn hit-node [event]
  "Takes .path property of a DOM event and returns first element with an id"
  (first (filter #(not (clojure.string/blank? (.-id %))) (.-path event))))

(defn ^:export main []
  (start! {:session app-session :schema app-schema :facts facts})
  (.addEventListener js/window "mousedown"
                     #(then-tuples {
                                    :action/type :mouse/down
                                    :mouse-down/target-id (hit-node %)
                                    :pos/x (.-clientX %)
                                    :pos/y (.-clientY %)}))
  (.addEventListener js/window "mousemove"
                     #(then-tuples {
                                    :action/type :mouse/move
                                    :pos/x (.-clientX %)
                                    :pos/y (.-clientY %)}))
  (let [element (.getElementById js/document "app")]
    (impi/mount :example @state element)
    (add-watch state ::mount (fn [_ _ _ s] (impi/mount :example s element)))))

(defonce x
         (animate state))
