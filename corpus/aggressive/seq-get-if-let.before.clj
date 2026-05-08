(defn tag-count [item]
  (if (seq (get item :tags))
    (count (get item :tags))
    0))
