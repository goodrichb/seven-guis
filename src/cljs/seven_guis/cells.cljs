(ns seven-guis.cells
  (:require
   [seven-guis.util :refer [log]]
   [reagent.core :as r]
   [reagent.dom :as dom]))

;; the capital letters, A-Z
(def columns
  (for [i (map (partial + 65) (range 26))]
    (.fromCharCode js/String i)))

(def rows (for [i (range 100)] i))

(def dblclick (r/atom nil))

(def edit (r/atom ""))

(def show-formulas? (r/atom false))

(def state (r/atom {}))

(def trim clojure.string/trim)

(defn reject-NaN [?n]
  (if (js/isNaN ?n)
    nil
    ?n))

(def functions #{"+" "-" "*" "SUM"})

(defn ?match-fn [token]
  (case token
    "+" +
    "-" -
    "*" *
    nil))

(defn literal [string]
  (let [trimmed (trim string)]
    (if-let [?number (reject-NaN (js/parseFloat (re-find #"^\d+\.?\d*$" trimmed)))]
      ?number
      trimmed)))

(defn ?form-or-literal [string]
  (let [trimmed (trim string)]
    (if-let [?number (reject-NaN (js/parseFloat (re-find #"^\d+\.?\d*$" trimmed)))]
      ?number
      (if-let [?reference (re-find #"^[A-Z]\d{1,2}$" trimmed)]
        (let [row (js/parseFloat (apply str (rest ?reference)))
              column (first ?reference)]
          [row column])
        (if (contains? functions trimmed)
          trimmed
          (if-let [?form (and (= "(" (first trimmed)) (= ")" (last trimmed)))]
            (let [inside (apply str (reverse (drop 1 (reverse (drop 1 trimmed)))))
                  tokens (clojure.string/split inside #"\s")]
              (map ?form-or-literal tokens))
            trimmed))))))

(defn ?parse [content]
  (if (= "=" (get content 0))
    (let [string (apply str (rest content))]
      (?form-or-literal string))
    (literal (str content))))

(defn lookup [[r c]]
  @(r/cursor state [[r c] :evaled]))

(defn sum-deps [[[r1 c1] [r2 c2]]]
  (let [rows (range r1 (inc r2))
        ci-1 (.charCodeAt c1 0)
        ci-2 (.charCodeAt c2 0)
        columns
        (for [code (range ci-1 (inc ci-2))]
          (js/String.fromCharCode code))]
    (mapcat #(for [c columns] [% c]) rows)))

(defn my-apply [f-name args]
  (case f-name
    "SUM"
    (let [deps (set (sum-deps args))]
      {:deps deps
       :evaled (apply + (map lookup deps))})
    "identity"
    {:deps (conj #{} args)
     :evaled (-> args lookup)}
    {:deps (set (filter vector? args))
     :evaled (if-let [f (?match-fn f-name)]
               (apply f (map lookup args))
               "UNRECOGNIZED-FN.ERROR")}))

(defn evaluate [[r c] parsed]
  (if (or (= [r c] parsed)
          (if (seq? parsed)
            (contains? (into #{} parsed) [r c])))
    {:deps #{}
     :evaled "SELF-REFERENCE.ERROR"}
    (if (seq? parsed)
      (let [f-name (first parsed)
            args (rest parsed)]
        (my-apply f-name args))
      (if (vector? parsed)
        (my-apply "identity" parsed)
        {:deps #{}
         :evaled parsed}))))

(defn quad [[r c] raw-input]
  (let [parsed (?parse raw-input)
        result (evaluate [r c] parsed)]
    (merge result
           {:raw raw-input
            :parsed parsed})))

(defn assoc-quad [state-map [r c] raw-input]
  (assoc state-map
         [r c]
         (quad [r c] raw-input)))

(defn initialize! []
  #_(log "initialize")
  ;; Ordered into sequential swap!s such that the initial evaluations succeed 
  (swap! state
         (fn [s]
           (-> s
               (assoc-quad [1 "B"] 5)
               (assoc-quad [2 "B"] 1)
               (assoc-quad [4 "C"] 8)
               (assoc-quad [1 "A"] 10)
               (assoc-quad [1 "E"] 1.5)
               (assoc-quad [2 "A"] 4))))
  (swap! state assoc-quad [3 "A"] "=(- A1 A2)")
  (swap! state assoc-quad [3 "C"] "=A3")
  (swap! state assoc-quad [0 "B"] "=(SUM B1 C4)")
  (swap! state assoc-quad [0 "E"] "=(+ B0 A3)")
  (swap! state assoc-quad [2 "E"] "=(* E0 E1)")
  #_(log @state))

(defn propogate!
  ;; Use the cell's existing :raw, but re-parse and evaluate,
  ;; and propogate changes
  ([[r c]] (propogate! [r c] @(r/cursor state [[r c] :raw])))
  ;; Parse new raw text, and propogate
  ([[r c] raw]
  (let [cursor (r/cursor state [[r c]])
        results (quad [r c] raw)
        downstream (map first (filter #(contains? (-> % second :deps)
                                                  [r c])
                                      @state))]
    #_(log "propogate! " [r c])
    #_(log "downstream: " downstream)
    (reset! cursor results)
    (doall (map propogate! downstream)))))

(defn save-cell-input! [[r c]]
  (propogate! [r c] @edit)
  (reset! edit "")
  (reset! dblclick nil))

(defn input [[r c] value]
  [:input {:type "text"
           :value @value
           :on-blur
           (fn [e]
             (save-cell-input! [r c]))
           :on-key-press
           (fn [e]
             ;; on enter
             (when (= 13 (.-charCode e))
               (save-cell-input! [r c])))
           :on-change #(reset! value (-> % .-target .-value))}])

(defn editing [props edit]
  (r/create-class {:component-did-mount
                   (fn [component] (.focus (dom/dom-node component)))
                   :reagent-render
                   (fn [props] [input props edit])}))

(defn formula? [q]
  (and (string? (some-> q :raw))
       (= "=" (some-> q :raw first))))

(defn inner-cell [c r]
  (if (= @dblclick [c r])
    [:td.cell.editing {:id (str c r)}
     [editing [r c] edit]]
    (let [cursor (r/cursor state [[r c]])]
      (if-let [content @cursor]
        [:td.cell {:id (str c r)
                   :class (if (formula? content) "formula")}
         (if (and (formula? content) @show-formulas?) [:div (str (:raw content))])
         (str (:evaled content))]
        [:td.cell.empty {:id (str c r)}]))))

(def cell
  (with-meta
   inner-cell
   {:component-did-mount
    (fn [this]
      (let [id (reverse (take 2 (reverse (r/argv this)))) ;; r/props didn't work
            cursor-path (vec (reverse id))
            el (.getElementById js/document (apply str id))]
       (.addEventListener el
                          "dblclick"
                          (fn [] (do
                            (reset! edit @(r/cursor state [(vec cursor-path) :raw]))
                            (reset! dblclick (vec id)))))))}))

(defn cells-component []
  [:div#cells.ui
   [:h2 "Cells"]
   [:p "Accepts formulas of the form: "
    [:code "=B0"]
    ", "
    [:code "=(+ A0 A1)"]
    ", "
    [:code "=(- A0 A1)"]
    ", "
    [:code "=(* A0 A1)"]
    ", and "
    [:code "=(SUM A0 C3)"]]
   [:button {:on-click #(swap! show-formulas? not)}
    (if @show-formulas?
      "Hide Formulas"
      "Show Formulas")]
   [:div#cells-wrapper
   [:table#cells-table
    [:tbody
     [:tr
      [:td.cell] ;; at the junction of the row/column labels
      ;; column labels
      (for [c columns]
        ^{:key (str "column-" c)}
        [:td.cell (str c)])]
     (for [r rows]
       ^{:key r}
       [:tr
        ;; row labels
        ^{:key (str "row-" r)}
        [:td.cell (str r)]
        ;; actual cells
        (for [c columns]
          ^{:key (str c r)}
          [cell c r])])]]]])

(def cells
  (with-meta cells-component
             {:component-did-mount initialize!}))
