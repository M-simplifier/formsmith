(defn tag-view [item]
  (when (seq (:tags item))
    [tag-list (:tags item)]))
