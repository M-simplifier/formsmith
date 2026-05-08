(let [ready? false blocked? false]
  (cond ready? :ready blocked? :blocked :else :waiting))
