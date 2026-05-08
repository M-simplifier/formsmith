(defn build-query [item]
  (cond-> []
    (not-empty (:tags item)) (conj :tags)))
