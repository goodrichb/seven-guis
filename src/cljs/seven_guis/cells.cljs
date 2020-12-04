(ns seven-guis.cells
  (:require
   [clojure.string :refer [trim]]
   [seven-guis.util :refer [log]]
   [reagent.core :as r]
   [reagent.dom :as dom]))

;;; State

(comment
 """state stores the spreadsheet's cell data.
   It is a map, keyed by [row column] vectors."""
   {[row column] <cell-data>}
   "<cell-data> is itself a map, generated by the cell-data function")
(def state (r/atom {}))

;; Ephemeral UI state tracking
(def dblclick (r/atom nil))
(def edit (r/atom ""))
(def show-formulas? (r/atom false))

;; the capital letters, A-Z, label columns
(def columns
  (for [i (map (partial + 65) (range 26))]
    (.fromCharCode js/String i)))

;; 0 to 99 label the rows
(def rows (for [i (range 100)] i))

;; supported functions
(def functions #{"+" "-" "*" "SUM"})

;;; Parsing

(defn reject-NaN [?n] (if (js/isNaN ?n) nil ?n))

(defn literal [string]
  (let [trimmed (trim string)]
    (if-let [?number (reject-NaN (js/parseFloat (re-find #"^\d+\.?\d*$" trimmed)))]
      ?number
      trimmed)))

(defn form-or-literal [string]
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
              (map form-or-literal tokens))
            trimmed))))))

(defn ?parse [content]
  (if (= "=" (get content 0))
    (let [string (apply str (rest content))]
      (form-or-literal string))
    (literal (str content))))

;;; Evaluation

(defn lookup [[r c]]
  @(r/cursor state [[r c] :evaluated]))

(defn sum-deps [[[r1 c1] [r2 c2]]]
  (let [rows (range r1 (inc r2))
        ci-1 (.charCodeAt c1 0)
        ci-2 (.charCodeAt c2 0)
        columns
        (for [code (range ci-1 (inc ci-2))]
          (js/String.fromCharCode code))]
    (mapcat #(for [c columns] [% c]) rows)))

(defn ?match-fn [token]
  (case token
    "+" +
    "-" -
    "*" *
    nil))

(defn apply-fn [f-name args]
  (case f-name
    "SUM"
    (let [deps (set (sum-deps args))] ;; not all deps are within parsed
      {:deps deps
       :evaluated (apply + (map lookup deps))})
    "identity"
    {:deps (conj #{} args)
     :evaluated (-> args lookup)}
    {:deps (set (filter vector? args))
     :evaluated (if-let [f (?match-fn f-name)]
                  (apply f (map lookup args))
                  "UNRECOGNIZED-FN.ERROR")}))

(defn self-reference? [[r c] parsed]
  (or (= [r c] parsed)
      (if (seq? parsed)
        (contains? (into #{} parsed) [r c]))))

(defn evaluate [[r c] parsed]
  (if (self-reference? [r c] parsed)
    {:deps #{}
     :evaluated "SELF-REFERENCE.ERROR"}
    (if (seq? parsed)
      ;; we've found a form, apply its function
      (let [f-name (first parsed)
            args (rest parsed)]
        (apply-fn f-name args))
      (if (vector? parsed)
        ;; we've found a cell reference
        (apply-fn "identity" parsed)
        ;; bottom out at parsed strings and numbers
        {:deps #{}
         :evaluated parsed}))))

(defn cell-data
  "return {:raw \"raw text input\"
           :parsed <parser output>
           :deps #{ [0 A] }
           :evaluated <calculated result>"
  [[r c] raw-input]
  (let [parsed (?parse raw-input)
        result (evaluate [r c] parsed)]
    (merge result
           {:raw raw-input
            :parsed parsed})))

(defn find-downstream [[r c]]
  (map first ;; we only need the key from the [k record] in state
       (filter #(contains? (-> % second :deps)
                           [r c])
               @state)))

(defn propogate!
  ;; With this arity, use the cell's existing :raw, but re-parse, re-evaluate,
  ;; and propogate changes
  ([[r c]] (propogate! [r c] @(r/cursor state [[r c] :raw])))
  ;; When a new :raw text is provided, parse and evaluate that
  ([[r c] raw]
  (let [cursor (r/cursor state [[r c]])
        results (cell-data [r c] raw)
        downstream (find-downstream [r c])]
    (comment
      (log "propogate! " [r c] " raw: " raw)
      (log "downstream: " downstream))
    (when-not (= "" raw) ;; prevent empty string cells
      ;; Check for circular dependencies
      (if-not (empty? (clojure.set/intersection (-> results :deps)
                                                (-> downstream set)))
        (do (js/alert "Circular dependency found. Ignoring last input.")
            #_(log @state))
        ;; Re-evaluate anything which depends on this cell
        (do (reset! cursor results)
            #_(log @state)
            (doall (map propogate! downstream))))))))

(defn save-cell-input! [[r c]]
  (propogate! [r c] @edit)
  (reset! edit "")
  (reset! dblclick nil))

;;; Initialization

(defn assoc-cell-data [state-map [r c] raw-input]
  (assoc state-map
         [r c]
         (cell-data [r c] raw-input)))

(defn initialize! []
  #_(log "initialize")
  ;; Ordered into sequential swap!s such that the initial evaluations succeed
  (swap! state
         (fn [s]
           (-> s
               (assoc-cell-data [1 "B"] 5)
               (assoc-cell-data [2 "B"] 1)
               (assoc-cell-data [4 "C"] 8)
               (assoc-cell-data [1 "A"] 10)
               (assoc-cell-data [1 "E"] 1.5)
               (assoc-cell-data [2 "A"] 4))))
  (swap! state assoc-cell-data [3 "A"] "=(- A1 A2)")
  (swap! state assoc-cell-data [3 "C"] "=A3")
  (swap! state assoc-cell-data [0 "B"] "=(SUM B1 C4)")
  (swap! state assoc-cell-data [0 "E"] "=(+ B0 A3)")
  (swap! state assoc-cell-data [2 "E"] "=(* E0 E1)")
  #_(log @state))

;;; Components

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
         (str (:evaluated content))]
        [:td.cell.empty {:id (str c r)}]))))

(def cell
  (with-meta
   inner-cell
   {:component-did-mount
    (fn [this]
      (let [;; id is [column row], the more natural order for formulas and humans
            id (vec (reverse (take 2 (reverse (r/argv this)))))
            ;; cursor-path is [row column], to be used with @state
            cursor-path (vec (reverse id))
            elem (.getElementById js/document (apply str id))]
       (.addEventListener elem
                          "dblclick"
                          (fn [] (do
                            (reset! edit @(r/cursor state [cursor-path :raw]))
                            (reset! dblclick id))))))}))

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
   [:button.formula-toggle {:on-click #(swap! show-formulas? not)}
    (if @show-formulas?
      "Hide Formulas"
      "Show Formulas")]
   [:div#cells-wrapper
   [:table#cells-table
    [:tbody
     [:tr
      [:td.cell] ;; empty cell at the junction of the row/column labels
      ;; column labels
      (for [c columns]
        ^{:key (str "column-label-" c)}
        [:td.cell (str c)])]
     (for [r rows]
       ^{:key r}
       [:tr
        ;; row labels
        ^{:key (str "row-label-" r)}
        [:td.cell (str r)]
        ;; actual cells
        (for [c columns]
          ^{:key (str c r)}
          [cell c r])])]]]])

(def cells
  (with-meta cells-component
             {:component-did-mount initialize!}))
