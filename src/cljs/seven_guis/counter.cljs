(ns seven-guis.counter
  (:require
   [reagent.core :as r]))

(def counter (r/atom 0))

(defn count! []
  (swap! counter inc))

(defn counter-component []
   [:div#counter.ui
    [:h2 "Counter"]
    [:label (str @counter)]
    [:button {:on-click count!} "Count"]])
