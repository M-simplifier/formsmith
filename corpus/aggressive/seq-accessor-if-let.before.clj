(defn tag-count [item]
  (if (seq (:tags item))
    (count (:tags item))
    0))
