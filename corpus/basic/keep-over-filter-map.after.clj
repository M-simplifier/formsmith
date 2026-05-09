(ns sample.keep-over-filter-map)

(defn normalized [users]
  (keep normalize users))
