(ns seven-guis.util)

(def log js/console.log)

(defn input [value]
  [:input {:type "text"
           :value @value
           :on-change #(reset! value (-> % .-target .-value))}])

(defn select [value options]
  [:select
   {:on-change #(reset! value (-> % .-target .-value keyword))
    :value @value}
   (for [o options]
     ^{:key (str "select-key-" (str (first o)))}
     [:option {:value (first o)} (second o)])])
