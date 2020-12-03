(ns seven-guis.crud
  (:require
   [seven-guis.util :refer [log input]]
   [reagent.core :as r]))

(def ?selected-person (r/atom 0))
(def people (r/atom {1 {:id 1 :name "Hans" :surname "Emil"}
                     2 {:id 2 :name "Max" :surname "Musermann"}
                     3 {:id 3 :name "Roman" :surname "Tisch"}}))
(def id-counter (r/atom 4))

(def prefix (r/atom "")) ;; on which to filter surnames
(def filtered-people (r/atom @people)) ;; those which should be visible

(def editing-name (r/atom ""))
(def editing-surname (r/atom ""))

(defn prepopulate-editing! [id]
  (let [person (get @people id)]
    (reset! editing-name (:name person))
    (reset! editing-surname (:surname person))))

(defn listbox [value options]
  [:select#listbox
   {:on-change #(let [id (-> % .-target .-value js/parseInt)]
                  (reset! value id)
                  (prepopulate-editing! id))
    :size 6
    :value @value}
   ;; this option prevents a confusing visual-only selection of the first
   ;; real option on startup
   ^{:key "listbox-0"}
   [:option {:value 0 :style {:display "none"}}]
   (for [o options]
     ^{:key (str "listbox-" (str (:id o)))}
     [:option
      {:value (:id o)}
      (str (:surname o) ", " (:name o))])])

(defn reset-all! []
  (reset! ?selected-person 0)
  (reset! editing-name "")
  (reset! editing-surname ""))

(defn create! []
  (let [id @id-counter]
    (swap! id-counter inc)
    (swap! people
           assoc
           id
           {:id id
            :name @editing-name
            :surname @editing-surname})
    (reset-all!)))

(defn update! []
  (let [id @?selected-person]
    (when (> id 0) ;; don't accept updates to the empty default
      (swap! people
             assoc
             id
             {:id id
              :name @editing-name
              :surname @editing-surname}))
    (reset-all!)))

(defn delete! []
  (let [id @?selected-person]
    (swap! people dissoc id)
    (reset-all!)))

(defn input-filter [value]
  [:input {:type "text"
           :value @value
           :size 10
           :on-change #(reset! value (-> % .-target .-value))}])

(defn filter-people [prefix people]
   (let [matches (filter #(re-find (re-pattern (str "^" prefix ".?"))
                                   (:surname %))
                         (vals people))
         ids (map :id matches)]
     (select-keys people ids)))

(add-watch
 prefix
 :filter-on-prefix
 (fn [_key _atom _old-state new-state]
   (let [filtered (filter-people new-state @people)
         ids (-> filtered keys set)]
     (reset! filtered-people filtered)
     ;; clear the selected person if they don't match the prefix
     (if-not (contains? ids @?selected-person) (reset-all!)))))

(add-watch
 people
 :re-filter-on-updates
 (fn [_key _atom _old-state new-state]
   (reset! filtered-people (filter-people @prefix new-state))))

(defn crud []
  [:div#crud.ui
   [:h2 "CRUD"]
   [:div#crud-wrapper
   [:div#crud-left.crud-half
    [:div.form-group
     [:label {:for "prefix"} "Filter prefix:"]
     [:span.align-right [input-filter prefix]]]
    [listbox ?selected-person (vals @filtered-people)]]
   [:div#crud-right.crud-half
    [:div.form-group
     [:label {:for "editing-name"} "Name:"]
     [:span.align-right [input editing-name]]]
    [:div.form-group
     [:label {:for "editing-surname"} "Surname:"]
     [:span.align-right [input editing-surname]]]]]
   [:div.buttons
    [:button {:on-click create!}
     "Create"]
    [:button {:on-click update!}
     "Update"]
    [:button {:on-click delete!}
     "Delete"]]])
