(defn tag-count [item]
  (if-let [tags (not-empty (:tags item))] (count tags) 0))
