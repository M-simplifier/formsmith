(let [params ["status=ready" "sort=priority-desc"]]
  (if-let [params (not-empty params)] (vec params) []))
