(let [users [{:id 1} {:id 2}]]
  (when (seq users)
    (rand-nth users)))
