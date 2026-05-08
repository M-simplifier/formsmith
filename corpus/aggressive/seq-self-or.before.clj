(let [prompt "hello"
      fallback "world"]
  (if (seq prompt)
    prompt
    fallback))
