(let [ok? false
      ready? false]
  (if ok?
    (do
      (cond ready? :ready true :waiting))
    nil))
