(ns seven-guis.views
  (:require
   [seven-guis.counter :refer [counter-component]]
   [seven-guis.temp :refer [temp]]
   [seven-guis.booker :refer [booker]]
   [seven-guis.timer :refer [timer]]
   [seven-guis.crud :refer [crud]]
   [seven-guis.circles :refer [circles]]
   [seven-guis.cells :refer [cells]]))

(defn main-panel []
  [:div
   [:div.ui
    [:h1 "7GUIs"]
    [:p "implemented by Brian Goodrich"]
    [:p "specified "
     [:a {:href "https://eugenkiss.github.io/7guis/tasks"} "here"]]]
   [counter-component]
   [temp]
   [booker]
   [timer]
   [crud]
   [circles]
   [cells]])
