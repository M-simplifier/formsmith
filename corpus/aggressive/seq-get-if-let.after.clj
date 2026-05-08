(defn tag-count [item]
  (if-let [tags (not-empty (get item :tags))] (count tags) 0))
