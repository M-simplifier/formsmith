(ns sample.thread-last-chain)

(defn active-ids [users]
  (->> users (filter active?) (map :id)))
