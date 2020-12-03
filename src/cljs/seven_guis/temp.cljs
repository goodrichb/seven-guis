(ns seven-guis.temp
  (:require
   [seven-guis.util :refer [log]]
   [reagent.core :as r]))

(defn f->c [fd] (* (- fd 32) (/ 5 9)))
(defn c->f [cd] (+ (* cd (/ 9 5)) 32))

(def c (r/atom -40))
(def f (r/atom (c->f -40)))

(defn convert! [unit value]
  (case unit
    :c (reset! f (c->f value))
    :f (reset! c (f->c value))))

(defn maybe-convert! [unit entry]
  (let [?int (js/parseInt entry)]
    (when-not (js/isNaN ?int)
      (convert! unit ?int))))

(defn temp-input [value unit]
  [:input {:type "text"
           :value @value
           :on-change #(let [entry (-> % .-target .-value)]
                        (reset! value entry)
                        (maybe-convert! unit entry))}])

(defn temp []
   [:div#temp.ui
    [:h2 "Temperature Converter"]
    [temp-input c :c]
    [:label "Celsius ="]
    [temp-input f :f]
    [:label "Fahrenheit"]])
