(defn build-query [q]
  (cond-> []
    (not-empty q) (conj q)))
