(defn build-query [q]
  (cond-> []
    (and q (seq q)) (conj q)))
