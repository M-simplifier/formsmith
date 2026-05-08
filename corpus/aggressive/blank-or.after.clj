(defn- matches-query? [item q]
  (or (str/blank? q) (matches-query* item q)))
