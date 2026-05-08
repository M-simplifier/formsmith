(ns cold-start-demo.ui
  (:require [clojure.string :as str]))

(defn detail-panel [status note]
  (let [headline (if (not status) "unknown" status)]
    (let [body (or note "No note yet.")]
      [:section
       [:h2 headline]
       [:p body]])))

(defn submit-state [saving? items]
  (if (and items (seq items))
    {:disabled saving?
     :label "Save changes"}
    {:disabled true
     :label "Add one item first"}))

(defn search-hint [query]
  (when (seq (str/trim (or query "")))
    [:p.hint "Filter is active"]))
