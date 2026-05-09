(ns sample.keep-over-filter-map)

(defn normalized [users]
  (filter some? (map normalize users)))
