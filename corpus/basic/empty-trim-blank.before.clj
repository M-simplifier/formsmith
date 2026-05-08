(defn blankish? [text]
  (empty? (str/trim (or text ""))))
