(defn tag-view [item]
  (when (seq (get-in item [:meta :tags]))
    [tag-list (get-in item [:meta :tags])]))
