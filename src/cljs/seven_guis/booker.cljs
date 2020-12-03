(ns seven-guis.booker
  (:require
   [seven-guis.util :refer [log select]]
   [reagent.core :as r]))

;; Dates are DD/MM/YYYY
(def date-regex #"^\d{2}\.\d{2}\.\d{4}$")
(defn parse-date [s]
  (if-let [matching-string
           (some->> s
                    clojure.string/trim
                    (re-find date-regex))]
    (let [[day month year] (clojure.string/split matching-string #"\.")
          ;; month is 0-indexed
          month-index (dec (js/parseInt month))]
      (js/Date. (js/parseInt year)
                month-index
                (js/parseInt day)))))

;;; STATE

(def flight-type (r/atom :one-way))

(def default-start-date "15.12.2020")
;; 50 years :) https://twitter.com/Conaw/status/1333196349362192385
(def default-return-date "15.12.2070")

;; These two track raw user input
(def start-date (r/atom default-start-date))
(def return-date (r/atom default-return-date))
;; These two will be nil, or a successfully parsed js/Date
(def start-date-js (r/atom (parse-date default-start-date)))
(def return-date-js (r/atom (parse-date default-return-date)))

;; .setHours here ensures we don't disallow flights today,
;; as happens with: (def today (js/Date.))
(def today (js/Date. (.setHours (js/Date.) 0 0 0 0)))

;; We'll log all booking messages in this atom
(def messages (r/atom []))



;; Best with cljs-devtools!
(defn log-booker-state []
  (log
   {:t @flight-type
    :s @start-date
    :r @return-date
    :s-js @start-date-js
    :r-js @return-date-js
    :today today}))

(defn store-parsed-date! [s parsed-store]
  (let [?parsed (parse-date s)]
    (reset! parsed-store ?parsed)
    #_(log-booker-state)))

(defn date-input
  ([value parsed-store] (date-input value parsed-store true))
  ([value parsed-store enabled?]
   [:input {:type "text"
            :value @value
            :disabled (not enabled?)
            :style {:background-color (if (some? @parsed-store)
                                        "white"
                                        "red")}
            :on-change #(let [entry (-> % .-target .-value)]
                         (reset! value entry)
                         (store-parsed-date! entry parsed-store))}]))

(defn bookable? [flight-type start-js return-js]
  (if (some? start-js)
    (case flight-type
      :one-way (>= start-js today)
      :return (and (some? return-js)
                   (>= start-js today)
                   (>= return-js start-js)))
    false))

(defn date->string [date]
  (str (.getDate date)
       "."
       (inc (.getMonth date))
       "."
       (.getFullYear date)))

(defn book! []
  (swap! messages
         conj
         (case @flight-type
           :one-way
           (str "You have booked a one-way flight on "
                (date->string @start-date-js)
                ".")
           :return
           (str "You have booked a flight on "
                (date->string @start-date-js)
                ", and a return flight on "
                (date->string @return-date-js)
                "."))))

(defn book-button []
  [:button {:type "button"
            :disabled
            (not (bookable? @flight-type @start-date-js @return-date-js))
            :on-click book!}
   "Book"])

(defn time-travel-warning []
  (if (and @start-date-js
           (< @start-date-js today))
    [:div "That's in the past!"]
    [:div.empty]))

(defn success-messages []
  [:div
   (map-indexed (fn [idx m]
                  ^{:key (str "message-" idx)}
                  [:div m])
                @messages)])

(defn booker []
  [:div#booker.ui
   [:h2 "Flight Booker"]
   [:div
    [select
     flight-type
     {:one-way "one-way flight"
      :return "return flight"}]]
   [:div [date-input start-date start-date-js]]
   [:div [date-input return-date return-date-js (= :return @flight-type)]]
   [:div [book-button]]
   [:div [time-travel-warning]]
   [success-messages]])
