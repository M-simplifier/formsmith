(ns formsmith.config
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [formsmith.fs :as fs]))

(def default-file ".formsmith.edn")

(def default-config
  {:ignore-paths []
   :rules {:only nil
           :exclude #{}}
   :baseline nil
   :suppressions []
   :report {:include-source? false}})

(defn- existing-file? [path]
  (and path
       (.exists (io/file path))
       (.isFile (io/file path))))

(defn- read-edn-file [path]
  (try
    (edn/read-string (slurp path))
    (catch Exception ex
      (throw (ex-info "Failed to read formsmith config"
                      {:path path}
                      ex)))))

(defn- normalize-rule-id [value]
  (cond
    (keyword? value) value
    (symbol? value) (keyword (str value))
    (string? value) (keyword value)
    (nil? value) nil
    :else (throw (ex-info "Invalid rule id" {:value value}))))

(defn- rule-id-set [values]
  (when-let [values (not-empty values)]
    (into #{} (map normalize-rule-id) values)))

(defn normalize-suppression [{:keys [rule-id scope] :as suppression}]
  (cond-> (select-keys suppression [:file :line :column :scope])
    rule-id (assoc :rule-id (normalize-rule-id rule-id))
    (nil? scope) (assoc :scope :finding)))

(defn- baseline-findings [path]
  (when (existing-file? path)
    (let [data (read-edn-file path)]
      (cond
        (map? data) (:findings data)
        (sequential? data) data
        :else (throw (ex-info "Invalid formsmith baseline"
                              {:path path
                               :expected "map with :findings or vector"}))))))

(defn normalize [config]
  (let [config (merge-with (fn [left right]
                             (if (and (map? left) (map? right))
                               (merge left right)
                               right))
                           default-config
                           (or config {}))
        baseline-path (:baseline config)
        baseline-suppressions (map #(assoc (normalize-suppression %)
                                           :source :baseline)
                                   (baseline-findings baseline-path))]
    (-> config
        (update :ignore-paths #(vec (or % [])))
        (update-in [:rules :only] rule-id-set)
        (update-in [:rules :exclude] #(or (rule-id-set %) #{}))
        (update :suppressions #(vec (map normalize-suppression (or % []))))
        (assoc :baseline-suppressions (vec baseline-suppressions)))))

(defn load-config [{:keys [config no-config baseline include-source]}]
  (let [path (cond
               no-config nil
               config config
               (existing-file? default-file) default-file)
        loaded (if path
                 (read-edn-file path)
                 {})
        merged (cond-> loaded
                 baseline (assoc :baseline baseline)
                 include-source (assoc-in [:report :include-source?] true))]
    (normalize merged)))

(defn rule-enabled? [config id]
  (let [id (normalize-rule-id id)
        only (get-in config [:rules :only])
        exclude (get-in config [:rules :exclude])]
    (and (or (nil? only) (contains? only id))
         (not (contains? exclude id)))))

(defn- normalize-path [path]
  (-> (fs/display-path path)
      (str/replace "\\" "/")
      (str/replace #"^\./" "")))

(defn- path-under? [path root]
  (let [path (normalize-path path)
        root (normalize-path root)]
    (or (= path root)
        (str/starts-with? path (str root "/")))))

(defn ignored-target? [config path]
  (boolean
   (some #(path-under? path %)
         (:ignore-paths config))))

(defn filter-targets [config targets]
  (->> targets
       (remove #(ignored-target? config %))
       vec))

(defn filter-findings [config findings]
  (->> findings
       (remove #(ignored-target? config (:file %)))
       vec))

(defn- parse-directive-rule [value]
  (when (and value
             (not (str/blank? value)))
    (normalize-rule-id value)))

(defn inline-suppressions [file source]
  (let [lines (str/split-lines source)]
    (vec
     (mapcat
      (fn [[idx line]]
        (let [line-no (inc idx)]
          (if (re-find #"\bformsmith-disable-file\b" line)
            [{:scope :file
              :file file
              :line line-no
              :source :inline}]
            (let [[next-match next-rule] (re-find #"\bformsmith-disable-next-line(?:\s+([A-Za-z0-9_.!?*+<>=/-]+))?" line)
                  [line-match line-rule] (re-find #"\bformsmith-disable-line(?:\s+([A-Za-z0-9_.!?*+<>=/-]+))?" line)]
              (cond-> []
                next-match (conj (cond-> {:scope :line
                                          :file file
                                          :line (inc line-no)
                                          :source :inline}
                                   next-rule (assoc :rule-id (parse-directive-rule next-rule))))
                line-match (conj (cond-> {:scope :line
                                          :file file
                                          :line line-no
                                          :source :inline}
                                   line-rule (assoc :rule-id (parse-directive-rule line-rule)))))))))
      (map-indexed vector lines)))))

(defn effective-suppressions [config file source]
  (vec
   (concat (:suppressions config)
           (:baseline-suppressions config)
           (inline-suppressions file source))))

(defn- same-file? [left right]
  (or (nil? left)
      (= (normalize-path left) (normalize-path right))))

(defn suppression-matches?
  [suppression {:keys [file rule-id line column]}]
  (and (same-file? (:file suppression) file)
       (or (= :file (:scope suppression))
           (and (or (nil? (:rule-id suppression))
                    (= (normalize-rule-id (:rule-id suppression))
                       (normalize-rule-id rule-id)))
                (or (nil? (:line suppression))
                    (= (:line suppression) line))
                (or (nil? (:column suppression))
                    (= (:column suppression) column))))))

(defn suppressed? [suppressions finding]
  (boolean
   (some #(suppression-matches? % finding)
         suppressions)))

(defn suppress-findings [config findings]
  (->> findings
       (remove (fn [{:keys [file] :as finding}]
                 (let [source (when (and file
                                         (.exists (io/file file)))
                                (slurp file))]
                   (suppressed? (effective-suppressions config file (or source ""))
                                finding))))
       vec))

(defn finding->baseline-entry [{:keys [file rule-id line column message tier]}]
  (cond-> {:file (fs/display-path file)
           :rule-id rule-id
           :line line
           :column column}
    message (assoc :message message)
    tier (assoc :tier tier)))

(defn baseline-data [findings summary]
  {:version 1
   :summary summary
   :findings (mapv finding->baseline-entry findings)})
