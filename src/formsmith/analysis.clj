(ns formsmith.analysis
  (:require [formsmith.fs :as fs]
            [formsmith.framework :as framework]
            [formsmith.kondo :as kondo]
            [formsmith.source :as source]))

(defn analyze-paths [paths]
  (:analysis (kondo/analyze-paths paths)))

(defn namespace-deps [analysis]
  (->> (:namespace-usages analysis)
       (keep (fn [{:keys [from to alias filename row col]}]
               (when (and from to)
                 {:from from
                  :to to
                  :alias alias
                  :file filename
                  :line row
                  :column col})))
       distinct
       (sort-by (juxt #(str (:from %))
                      #(str (:to %))
                      :file
                      :line
                      :column))
       vec))

(defn namespace-definitions [analysis]
  (->> (:namespace-definitions analysis)
       (mapv (fn [{:keys [name filename row col]}]
               {:name name
                :file filename
                :line row
                :column col}))
       (sort-by (juxt :name :file))))

(defn summary [analysis]
  {:namespaces (count (:namespace-definitions analysis))
   :namespace-deps (count (namespace-deps analysis))
   :vars (count (:var-definitions analysis))
   :var-usages (count (:var-usages analysis))
   :locals (count (:locals analysis))
   :local-usages (count (:local-usages analysis))})

(defn local-usage? [analysis file symbol line column]
  (boolean
   (some (fn [{:keys [filename name row col]}]
           (and (= file filename)
                (= symbol name)
                (= line row)
                (= column col)))
         (:local-usages analysis))))

(defn var-usage? [analysis file symbol target-ns line column]
  (boolean
   (some (fn [{:keys [filename name to row col name-row name-col]}]
           (and (= file filename)
                (= symbol name)
                (= target-ns to)
                (= line (or name-row row))
                (= column (or name-col col))))
         (:var-usages analysis))))

(defn- source-scan-dep? [dep]
  (= :source-scan (:source dep)))

(defn- better-dep [existing candidate]
  (cond
    (nil? existing) candidate
    (and (source-scan-dep? existing)
         (not (source-scan-dep? candidate))) candidate
    :else existing))

(defn- dep-key [{:keys [from to alias file]}]
  [(str from) (str to) (str alias) (fs/display-path file)])

(defn- merge-namespace-deps [deps]
  (->> deps
       (reduce (fn [acc dep]
                 (let [key (dep-key dep)]
                   (update acc key better-dep dep)))
               {})
       vals))

(defn project-facts [paths]
  (let [analysis (analyze-paths paths)
        namespace-deps (->> (merge-namespace-deps
                             (concat (namespace-deps analysis)
                                     (source/namespace-deps-from-paths paths)))
                            distinct
                            (sort-by (juxt #(str (:from %))
                                           #(str (:to %))
                                           :file
                                           :line
                                           :column))
                            vec)
        frameworks (framework/detect namespace-deps)]
    {:summary (assoc (summary analysis)
                     :frameworks (count frameworks))
     :namespaces (namespace-definitions analysis)
     :namespace-deps namespace-deps
     :frameworks frameworks}))
