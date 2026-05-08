(ns formsmith.main
  (:gen-class)
  (:require [clojure.string :as str]
            [clojure.tools.cli :refer [parse-opts]]
            [formsmith.analysis :as analysis]
            [formsmith.catalog :as catalog]
            [formsmith.contract :as contract]
            [formsmith.engine :as engine]
            [formsmith.fs :as fs]
            [formsmith.kondo :as kondo]
            [formsmith.report :as report]))

(def cli-options
  [["-j" "--json" "Emit JSON output"]
   [nil "--canonical" "Use the canonical autofix profile"]
   [nil "--guarded" "Apply analyzer-guarded rewrites when static facts allow it"]
   [nil "--aggressive" "Apply semantic-pattern rewrites too"]
   [nil "--check" "Report files that would change without writing"]
   [nil "--no-kondo" "Disable clj-kondo diagnostics in lint output"]
   [nil "--rewrite-only" "Skip formatter phase during fix"]
   ["-h" "--help" "Show help"]])

(defn- usage []
  (str/join
   "\n"
   ["formsmith"
    ""
    "Usage:"
    "  formsmith lint <paths...>"
    "  formsmith check <paths...>"
    "  formsmith fix <paths...>"
    "  formsmith fmt <paths...>"
    "  formsmith analyze <paths...>"
    "  formsmith profiles <paths...>"
    "  formsmith contracts <paths...>"
    "  formsmith rules"
    "  formsmith explain <rule-id>"
    ""
    "Options:"
    "  -j, --json  Emit JSON output"
    "      --canonical  Use the canonical autofix profile"
    "      --guarded  Apply analyzer-guarded rewrites when static facts allow it"
    "      --aggressive  Apply semantic-pattern rewrites too"
    "      --check  Report files that would change without writing"
    "      --no-kondo  Disable clj-kondo diagnostics in lint output"
    "      --rewrite-only  Skip formatter phase during fix"
    "  -h, --help  Show help"]))

(defn- normalize-command [command]
  (case command
    "lint" :lint
    "check" :check
    "fix" :fix
    "fmt" :format
    "analyze" :analyze
    "profiles" :profiles
    "contracts" :contracts
    "rules" :rules
    "explain" :explain
    nil))

(defn- report-output [results summary json?]
  (println
   (if json?
     (report/json-report results summary)
     (report/text-report results summary))))

(defn- contracts-output [contracts summary json?]
  (println
   (if json?
     (report/data-json-report {:contracts contracts :summary summary})
     (contract/text-report contracts summary))))

(defn -main [& args]
  (let [[command & more] args
        mode (normalize-command command)
        {:keys [options arguments errors]} (parse-opts more cli-options)
        paths arguments
        context {:mode mode
                 :aggressive? (:aggressive options)
                 :format? (not (:rewrite-only options))
                 :write? (not (:check options))}]
    (cond
      (:help options) (println (usage))
      errors (do
               (doseq [error errors]
                 (binding [*out* *err*]
                   (println error)))
               (System/exit 1))
      (nil? mode) (do
                    (binding [*out* *err*]
                      (println (usage)))
                    (System/exit 1))
      (= :rules mode) (println (report/rules-report (catalog/all-rule-summaries)))
      (and (= :analyze mode) (empty? paths)) (do
                                               (binding [*out* *err*]
                                                 (println "No input paths provided."))
                                               (System/exit 1))
      (= :analyze mode) (if (:json options)
                          (println (report/data-json-report (analysis/project-facts paths)))
                          (println (report/analysis-report (analysis/project-facts paths))))
      (and (= :profiles mode) (empty? paths)) (do
                                                (binding [*out* *err*]
                                                  (println "No input paths provided."))
                                                (System/exit 1))
      (= :profiles mode) (let [missing-paths (fs/missing-paths paths)]
                           (when-let [missing-paths (not-empty missing-paths)]
                             (doseq [path missing-paths]
                               (binding [*out* *err*]
                                 (println (str "Input path does not exist: " path))))
                             (System/exit 1))
                           (let [facts (analysis/project-facts paths)
                                 frameworks (:frameworks facts)]
                             (println
                              (if (:json options)
                                (report/data-json-report {:frameworks frameworks
                                                          :summary (select-keys (:summary facts)
                                                                                [:frameworks])})
                                (report/frameworks-report frameworks)))))
      (and (= :contracts mode) (empty? paths)) (do
                                                 (binding [*out* *err*]
                                                   (println "No input paths provided."))
                                                 (System/exit 1))
      (= :contracts mode) (let [targets (fs/discover-targets paths :lint)
                                missing-paths (fs/missing-paths paths)]
                            (when-let [missing-paths (not-empty missing-paths)]
                              (doseq [path missing-paths]
                                (binding [*out* *err*]
                                  (println (str "Input path does not exist: " path))))
                              (System/exit 1))
                            (let [results (mapv #(engine/process-file %
                                                                      {:mode :lint})
                                                targets)
                                  contracts (contract/contracts-from-results results)
                                  summary (contract/summarize contracts)]
                              (contracts-output contracts summary (:json options))))
      (and (= :explain mode) (empty? paths)) (do
                                               (binding [*out* *err*]
                                                 (println "No rule id provided."))
                                               (System/exit 1))
      (= :explain mode) (if-let [rule (catalog/find-rule (first paths))]
                          (println (report/explain-report (catalog/rule->summary rule)))
                          (do
                            (binding [*out* *err*]
                              (println (str "Unknown rule: " (first paths))))
                            (System/exit 1)))
      (empty? paths) (do
                       (binding [*out* *err*]
                         (println "No input paths provided."))
                       (System/exit 1))
      :else (let [engine-mode (if (= :check mode) :fix mode)
                  check-command? (= :check mode)
                  targets (fs/discover-targets paths engine-mode)
                  analyzer-facts (when (:guarded options)
                                   (analysis/analyze-paths targets))
                  effective-context (assoc context
                                           :mode engine-mode
                                           :canonical? (or check-command?
                                                           (:canonical options))
                                           :guarded? (:guarded options)
                                           :analysis analyzer-facts
                                           :write? (and (not check-command?)
                                                        (not (:check options))))
                  local-results (mapv #(engine/process-file % effective-context) targets)
                  results (if (and (= :lint mode)
                                   (not (:no-kondo options)))
                            (engine/merge-findings local-results (kondo/findings-for-paths paths))
                            local-results)
                  report-results (if (or check-command?
                                          (and (= :fix mode)
                                               (:check options)))
                                   (engine/preview-results results)
                                   results)
                  summary (engine/summarize-findings report-results)]
              (report-output report-results summary (:json options))
              (when (or (and (= :lint mode)
                             (pos? (:findings summary)))
                        (and (or check-command? (:check options))
                             (pos? (:changed (engine/summarize-findings results)))))
                (System/exit 2))))))
