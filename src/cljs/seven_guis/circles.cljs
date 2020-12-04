(ns seven-guis.circles
  (:require
   [seven-guis.util :refer [log]]
   [reagent.core :as r]))

;;; UI State

;; This will track the closest circle to the mouse position, up
;; until we are adjusting that circle, when we'll pause selection updates
(def selected (r/atom nil))
;; Latest mouse coordinates on the canvas
(def mouse (r/atom [0 0]))
;; Coordinates if the user has right clicked on a circle, nil otherwise
(def right-clicked-coords (r/atom nil))
;; [[x y] r] of a right-clicked-circle, or nil
(def right-clicked-circle (r/atom nil))
;; Are we adjusting a circle's radius? Should that dialog be up?
(def adjusting? (r/atom false))

;; We don't want every tick of the slider to be in the undo history
(def new-radius (r/atom 10))

;;; State tracked for Undo/Redo

;; start the state (index) at the end of the (synthetic) history
(def state (r/atom 1))

(def default-radius 10)
(def one-circle {[100 100] default-radius})
;; start with one circle
(def history (r/atom [{} one-circle]))

(defn at-rest? []
  (and (nil? @right-clicked-coords)
       (false? @adjusting?)))

(defn undoable? []
  (and (at-rest?) (>= @state 1)))

(defn redoable? []
  (and (at-rest?) (> (dec (count @history)) @state)))

(defn log-time-travel [action]
  (log action)
  (log @history)
  (log (count @history))
  (log @state)
  (log "undoable?: " (undoable?))
  (log "redoable?: " (redoable?)))

(defn undo! []
  #_(log-time-travel :undo)
  (when (undoable?)
    (swap! state dec)))

(defn redo! []
  #_(log-time-travel :redo)
  (when (redoable?)
    (swap! state inc)))

(defn get-circles []
  (get @history @state))

;;; Event handlers and atom watchers

(defn adjust! []
  (let [[[x y] old-radius] @right-clicked-circle]
    (reset! adjusting? true)
    (reset! new-radius old-radius) ;; prepopulate slider with existing value
    (reset! right-clicked-coords nil)))

(defn safe-update! [new-circles]
  (let [latest? (= @state (-> history deref count dec))]
    (if latest?
      (swap! history conj new-circles)
      (let [new-past (subvec @history 0 (inc @state))]
        (reset! history (conj new-past new-circles))))
    (swap! state inc)))

(defn save-radius! []
  (let [circle @right-clicked-circle
        circles (get-circles)
        r @new-radius]
    (reset! adjusting? false)
    (reset! right-clicked-circle nil)
    (reset! new-radius default-radius)
    (safe-update!  (assoc circles
                          (first circle)
                          r))))

(defn draw-circle! [context x y r highlight?]
  (do (.beginPath context)
      (.arc context x y r 0 (* 2 js/Math.PI) false)
      (set! (.-fillStyle context)
            (if highlight? "grey" "white"))
      (.fill context)
      (.stroke context)))

(defn draw!
  ([] (draw! (get @history @state) @selected))
  ([circles selection]
  (let [el (.getElementById js/document "canvas")
        context (.getContext el "2d")]
    (.clearRect context 0 0 (.-width el) (.-height el))
    (doall
      (for [[[x y] r :as circle] (reverse (sort-by second circles))]
        (draw-circle! context x y r (= selection circle)))))))

(add-watch
 new-radius
 :respond-to-slider
 (fn [_key _atom _old-state new-state]
   (let [circles (get-circles)
         [coords old-radius] @right-clicked-circle
         updated-circles
         (assoc circles
                coords
                new-state)]
     (draw! updated-circles [coords new-state]))))

(add-watch
 selected
 :new-selection
 (fn [_key _atom _old-state _new-state]
   (draw!)))

(add-watch
 state
 :index-change
 (fn [_key _atom _old-state _new-state]
   (draw!)))

(defn hit-test [[x y] circles]
  (let [hits (filter (fn [[[cx cy] r]]
                       (< (js/Math.hypot (- x cx) (- y cy)) r))
                     circles)
        smallest (first (sort-by second hits))]
    smallest))

(add-watch
 mouse
 :change-selected?
 (fn [_key _atom _old-state new-state]
   (when-not @adjusting?
     (let [circles (get @history @state)
           ?hit (hit-test new-state circles)]
       (reset! selected ?hit)))))

(defn show-context-menu! [event]
  (let [x (.-layerX event)
        y (.-layerY event)]
    (when-let [hit (hit-test [x y] (get-circles))]
      (reset! right-clicked-coords [x y])
      (reset! right-clicked-circle hit))))

(defn layer-coords [event]
  (let [x (.-layerX event)
        y (.-layerY event)]
    [x y]))

(defn mousemove [event]
  (reset! mouse (layer-coords event)))

(defn add-circle! [[x y]]
    (reset! selected [[x y] default-radius])
    (safe-update!  (assoc (get-circles)
                          [x y]
                          default-radius)))

(defn clear-right-click! []
  (reset! right-clicked-coords nil)
  (reset! right-clicked-circle nil))

(defn canvas-click [event]
  (let [[x y] (layer-coords event)
        selection @selected]
    (if (some? @right-clicked-coords)
      (clear-right-click!)
      (if (nil? selection) ;; empty space
        (add-circle! [x y])))))

(defn on-canvas []
  (let [el (.getElementById js/document "canvas")]
    (.addEventListener el
                       "contextmenu"
                       (fn [event]
                         (.preventDefault event)
                         (show-context-menu! event)
                         false)
                       false)
    (.addEventListener el
                       "mousemove"
                       mousemove
                       false)
    (.addEventListener el
                       "click"
                       canvas-click
                       false)
    (draw!)))

;;; Components

(defn undo []
   [:button {:type "button"
             :disabled (not (undoable?))
             :on-click undo!}
    "Undo"])

(defn redo []
   [:button {:type "button"
             :disabled (not (redoable?))
             :on-click redo!}
    "Redo"])

(defn time-travel []
  [:div.buttons [undo] [redo]])

(defn canvas []
  [:canvas#canvas
   {:width "760px"
    :height "500px"}])

(def canvas-with-hooks
  (with-meta
   canvas
   {:component-did-mount on-canvas}))

(defn popup []
  (let [[x y] @right-clicked-coords]
    (if (and x y)
      [:div
       [:button
        {:on-click adjust!
         :style {:position "absolute"
                 :top (str y "px")
                 :left (str x "px")}}
        "Adjust diameter"]]
      [:div.empty])))

(defn adjuster []
  (if @adjusting?
    (let [[[x y] old-radius] @right-clicked-circle]
      [:div {:style {:background-color "#eee"
                     :border "1px solid black"
                     :padding "10px"
                     :position "absolute"
                     :top (str 100 #_y "px")
                     :width "250px"
                     :height "100px"
                     :left (str 115 #_x "px")}}
       [:div {:style {:margin "auto"}}
       [:p "Adjust diameter of circle at (" x ", " y ")."]
       [:div
       [:input {:type "range"
                :id "radius"
                :min 1
                :max 350
                :value @new-radius
                :on-change
                #(reset! new-radius (-> % .-target .-value js/parseInt))
                :step 1}]
        (str (* 2 @new-radius) "px")]
       [:div [:button {:on-click save-radius!} "Save new diameter"]]]])
    [:div.empty]))

(defn circles []
  [:div#circles.ui
   [:h2 "Circle Drawer"]
   [:p "Click to add a circle. Right-click a circle to adjust its diameter."]
   [time-travel]
   [:div#canvas-wrapper
    [canvas-with-hooks]
    [popup]
    [adjuster]]])
