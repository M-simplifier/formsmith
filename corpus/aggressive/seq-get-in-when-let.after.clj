(defn tag-view [item]
  (when-let [tags (not-empty (get-in item [:meta :tags]))] [tag-list tags]))
