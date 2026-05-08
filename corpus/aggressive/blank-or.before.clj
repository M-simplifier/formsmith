(defn- matches-query? [item q]
  (if (str/blank? q)
    true
    (matches-query* item q)))
