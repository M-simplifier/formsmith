(defn tag-view [item]
  (when-let [tags (not-empty (:tags item))] [tag-list tags]))
