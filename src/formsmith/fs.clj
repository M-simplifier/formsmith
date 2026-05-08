(ns formsmith.fs
  (:require [clojure.java.io :as io]
            [formsmith.util :as util]))

(defn- path-file [path]
  (.getCanonicalFile (io/file path)))

(defn- walk-files [root accept?]
  (let [file (path-file root)]
    (cond
      (not (.exists file)) []
      (.isDirectory file) (->> (file-seq file)
                               (filter #(.isFile %))
                               (map #(.getPath %))
                               (filter accept?)
                               sort
                               vec)
      (accept? (.getPath file)) [(.getPath file)]
      :else [])))

(defn discover-targets [paths mode]
  (let [accept? (case mode
                  :format util/formatting-file?
                  util/clojure-file?)]
    (->> paths
         (mapcat #(walk-files % accept?))
         distinct
         vec)))

(defn missing-paths [paths]
  (->> paths
       (remove #(.exists (io/file %)))
       vec))

(defn display-path [path]
  (try
    (let [root (.normalize (.toAbsolutePath (.toPath (io/file "."))))
          target (.normalize (.toAbsolutePath (.toPath (io/file path))))]
      (str (.relativize root target)))
    (catch IllegalArgumentException _
      (str path))))
