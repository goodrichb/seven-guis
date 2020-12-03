(ns seven-guis.timer
  (:require
   [seven-guis.util :refer [log]]
   [reagent.core :as r]))

(defn gauge [elapsed duration]
  [:div
   [:label {:for "timer-progress"} "Elapsed Time:"]
   [:progress {:id "timer-progress"
               :max @duration
               :value @elapsed}]])

(defn elapsed-component [elapsed duration]
  [:div (str @elapsed "s of " @duration "s timer")])

(defn slider [duration]
  [:div
   [:label {:for "duration"} "Duration:"]
   [:input {:type "range"
            :id "duration"
            :name "duration"
            :min 1
            :max 1000
            :value @duration
            :on-change
            #(reset! duration (-> % .-target .-value js/parseInt))
            :step 1}]])

(defn reset [elapsed]
  [:div
   [:button {:on-click #(reset! elapsed 0)}
    "Reset"]])

(defn timer []
  (let [elapsed (r/atom 0)
        duration (r/atom 100)
        ;; track if there's a js/setInterval going, and the id to stop it
        interval-id (r/atom nil)
        start-timer!
        (fn []
          (reset!
           interval-id
           (js/setInterval #(swap! elapsed inc) 1000)))
        stop-timer!
        (fn []
          (js/clearInterval @interval-id)
          (reset! interval-id nil))]
    (fn []
      ;; Intialize ticking
      (start-timer!)
      ;; Stop the timer when it is done
      (add-watch
       elapsed
       :done?
       (fn [_k _atom old-state new-state]
         (when (>= new-state @duration)
           (stop-timer!))))
      ;; Watch for an extension of the timer,
      ;; iff it's been stopped
      (add-watch
       duration
       :extended?
       (fn [_k _atom old-state new-state]
         (when (and (nil? @interval-id)
                    (> new-state @elapsed))
           (start-timer!))))
      [:div#timer.ui
       [:h2 "Timer"]
       [gauge elapsed duration]
       [elapsed-component elapsed duration]
       [slider duration]
       [reset elapsed]])))
