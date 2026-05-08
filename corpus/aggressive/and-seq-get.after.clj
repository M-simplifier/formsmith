(defn build-query [item]
  (cond-> []
    (not-empty (get item :tags)) (conj :tags)))
