(defn build-query [q]
  (cond-> []
    (and q (seq q)) ; keep explicit check
    (conj q)))
