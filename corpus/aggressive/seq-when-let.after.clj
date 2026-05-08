(let [users [{:id 1} {:id 2}]]
  (when-let [users (not-empty users)] (rand-nth users)))
