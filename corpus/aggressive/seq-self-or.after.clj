(let [prompt "hello"
      fallback "world"]
  (or (not-empty prompt) fallback))
