(ns formsmith.util
  (:require [clojure.string :as str]))

(def clojure-extensions
  #{".clj" ".cljs" ".cljc" ".bb"})

(def formatting-extensions
  (conj clojure-extensions ".edn"))

(defn file-extension [path]
  (some-> path
          (str/split #"\.")
          last
          (some->> (str "."))))

(defn clojure-file? [path]
  (contains? clojure-extensions (file-extension path)))

(defn formatting-file? [path]
  (contains? formatting-extensions (file-extension path)))
