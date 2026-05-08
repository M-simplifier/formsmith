(defn build-query [item]
  (cond-> []
    (and (get item :tags) (seq (get item :tags))) (conj :tags)))
