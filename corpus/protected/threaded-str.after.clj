(some-> path
        (clojure.string/split #"\.")
        last
        (str "."))
