(ns sample.thread-last-chain)

(defn active-ids [users]
  (map :id (filter active? users)))
