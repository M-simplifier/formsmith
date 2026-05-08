(defn- create-ai-comment! [text]
  (when (seq (str/trim (or text "")))
    (println text)))
