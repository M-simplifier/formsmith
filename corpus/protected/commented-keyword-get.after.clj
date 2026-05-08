(ns corpus.protected.commented-keyword-get)

(defn card-title [card]
  (get card ; keep local note
       :title))
