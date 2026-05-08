(ns formsmith.corpus-test
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [formsmith.engine :as engine]))

(defn- slurp-resource [path]
  (slurp (io/file path)))

(defn- protected-case? [path]
  (str/includes? path "/protected/"))

(defn- aggressive-case? [path]
  (str/includes? path "/aggressive/"))

(defn- corpus-pairs []
  (let [root (io/file "corpus")]
    (->> (file-seq root)
         (filter #(.isFile %))
         (map #(.getPath %))
         (filter #(str/ends-with? % ".before.clj"))
         sort
         (mapv (fn [before]
                 {:name (-> before
                            (str/replace #"^corpus/" "")
                            (str/replace #"\.before\.clj$" ""))
                  :before before
                  :after (str/replace before #"\.before\.clj$" ".after.clj")})))))

(deftest corpus-golden-tests
  (doseq [{:keys [name before after]} (corpus-pairs)]
    (testing name
      (let [input (slurp-resource before)
            expected (slurp-resource after)
            {:keys [source findings]}
            (engine/process-source input
                                   {:file before
                                    :mode :fix
                                    :aggressive? (aggressive-case? before)})]
        (is (= expected source))
        (when-not (protected-case? before)
          (is (seq findings)))))))
