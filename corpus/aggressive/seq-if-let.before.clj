(let [params ["status=ready" "sort=priority-desc"]]
  (if (seq params)
    (vec params)
    []))
