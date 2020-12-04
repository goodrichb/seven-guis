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
    [:ul
     [:li "implemented by "
      [:a {:href "https://briangoodrich.com"}
       "Brian Goodrich"]]
     [:li [:a {:href "https://github.com/goodrichb/seven-guis"}
      "Source on GitHub"]]
     [:li [:a {:href "https://eugenkiss.github.io/7guis/tasks"} "7GUIs Benchmark"]]]]
   [counter-component]
   [temp]
   [booker]
   [timer]
   [crud]
   [circles]
   [cells]])
