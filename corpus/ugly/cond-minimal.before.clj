(let [ready? false]
  (cond
    ready? :ready
    :else :waiting))
