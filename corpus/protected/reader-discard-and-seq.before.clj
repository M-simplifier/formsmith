(ns sample.reader-discard-and-seq)

(defn render [xs]
  (when (and xs
             #_(seq xs)
             (seq xs))
    [:div "ready"]))
