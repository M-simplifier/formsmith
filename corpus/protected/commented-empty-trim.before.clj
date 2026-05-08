(defn blankish? [text]
  (empty? (str/trim text)) ; keep direct trim semantics
  )
