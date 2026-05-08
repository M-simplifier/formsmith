(ns formsmith.engine
  (:require [formsmith.format.backend :as backend]
            [formsmith.format.cljfmt :as cljfmt]
            [formsmith.rewrite :as rewrite]))

(defn- format-only [source formatter]
  (backend/format-source formatter source nil))

(defn- formatting-enabled? [context]
  (not= false (:format? context)))

(defn- writing-enabled? [context]
  (not= false (:write? context)))

(defn process-source
  ([source context]
   (process-source source context cljfmt/default-backend))
  ([source context formatter]
   (case (:mode context)
     :format {:source (format-only source formatter)
              :findings []}
     :fix (let [{rewritten :source findings :findings} (rewrite/rewrite-string source context)
                output (if (formatting-enabled? context)
                         (format-only rewritten formatter)
                         rewritten)]
            {:source output
             :findings findings})
     :lint (rewrite/rewrite-string source context)
     (throw (ex-info "Unsupported engine mode" {:mode (:mode context)})))))

(defn process-file
  ([file context]
   (process-file file context cljfmt/default-backend))
  ([file context formatter]
     (let [source (slurp file)
         result (process-source source (assoc context :file file) formatter)
         changed? (not= source (:source result))]
     (when (and changed?
                (#{:fix :format} (:mode context))
                (writing-enabled? context))
       (spit file (:source result)))
     (assoc result :file file :changed? changed?))))

(defn summarize-findings [results]
  {:files (count results)
   :changed (count (filter :changed? results))
   :findings (reduce + 0 (map #(count (:findings %)) results))})

(defn preview-results [results]
  (mapv (fn [result]
          (update result :findings
                  (fn [findings]
                    (->> findings
                         (filter :applied?)
                         vec))))
        results))

(defn- same-position? [left right]
  (and (= (:line left) (:line right))
       (= (:column left) (:column right))))

(defn- nested-let-overlap? [left right]
  (let [base-line (:line left)
        target-line (:line right)]
    (and (integer? base-line)
         (integer? target-line)
         (<= 0 (- target-line base-line) 6))))

(defn- duplicate-kondo-finding? [local-findings finding]
  (case (:rule-id finding)
    :kondo/redundant-str-call
    (some #(and (= :str/redundant-string-literal (:rule-id %))
                (same-position? % finding))
          local-findings)

    :kondo/redundant-let
    (some #(and (= :let/nested-let (:rule-id %))
                (nested-let-overlap? % finding))
          local-findings)

    false))

(defn merge-findings [results findings]
  (->> findings
       (reduce (fn [acc finding]
                 (update acc
                         (:file finding)
                         (fn [result]
                           (let [base (or result
                                          {:file (:file finding)
                                           :changed? false
                                           :findings []})]
                             (if (duplicate-kondo-finding? (:findings base) finding)
                               base
                               (update base :findings conj finding))))))
               (into (sorted-map)
                     (map (juxt :file identity) results)))
       vals
       vec))
