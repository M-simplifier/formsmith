(ns cold-start-demo.report
  (:require [clojure.string :as str]))

(defn status-label [ready?]
  (if (not ready?) "pending" "ready"))

(defn query-label [q]
  (if (empty? (str/trim (or q "")))
    "all items"
    (str/trim q)))

(defn welcome-banner []
  (str "Run review"))

(defn publishable? [item]
  (cond
    (:archived? item) false
    true (not (:draft? item))))
