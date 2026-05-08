(ns formsmith.main
  (:gen-class)
  (:require [clojure.string :as str]
            [clojure.tools.cli :refer [parse-opts]]
            [formsmith.analysis :as analysis]
            [formsmith.catalog :as catalog]
            [formsmith.config :as config]
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
   ["-c" "--config PATH" "Read an explicit formsmith config file"]
   [nil "--no-config" "Ignore .formsmith.edn"]
   [nil "--baseline PATH" "Suppress findings from a baseline EDN file"]
   [nil "--include-source" "Include source text and before/after snippets in JSON output"]
   ["-o" "--output PATH" "Write baseline output to a file"]
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
    "  formsmith baseline <paths...>"
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
    "  -c, --config PATH  Read an explicit formsmith config file"
    "      --no-config  Ignore .formsmith.edn"
    "      --baseline PATH  Suppress findings from a baseline EDN file"
    "      --include-source  Include source text and snippets in JSON output"
    "  -o, --output PATH  Write baseline output to a file"
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
    "baseline" :baseline
    "rules" :rules
    "explain" :explain
    nil))

(defn- report-output [results summary json? report-options]
  (println
   (if json?
     (report/json-report results summary report-options)
     (report/text-report results summary))))

(defn- contracts-output [contracts summary json? report-options]
  (println
   (if json?
     (report/data-json-report {:contracts (if (:include-source? report-options)
                                            contracts
                                            (mapv contract/scrub-contract contracts))
                               :summary summary})
     (contract/text-report contracts summary))))

(defn- all-findings [results]
  (mapcat :findings results))

(defn- write-output! [output path]
  (if path
    (spit path output)
    (println output)))

(defn -main [& args]
  (let [[command & more] args
        mode (normalize-command command)
        {:keys [options arguments errors]} (parse-opts more cli-options)
        paths arguments
        loaded-config (delay (config/load-config options))
        context {:mode mode
                 :aggressive? (:aggressive options)
                 :format? (not (:rewrite-only options))
                 :write? (not (:check options))
                 :config @loaded-config
                 :rule-enabled? #(config/rule-enabled? @loaded-config %)}]
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
      (= :contracts mode) (let [targets (config/filter-targets @loaded-config
                                                               (fs/discover-targets paths :lint))
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
                              (contracts-output contracts
                                                summary
                                                (:json options)
                                                (:report @loaded-config))))
      (and (= :baseline mode) (empty? paths)) (do
                                                (binding [*out* *err*]
                                                  (println "No input paths provided."))
                                                (System/exit 1))
      (= :baseline mode) (let [targets (config/filter-targets @loaded-config
                                                              (fs/discover-targets paths :lint))
                               missing-paths (fs/missing-paths paths)]
                           (when-let [missing-paths (not-empty missing-paths)]
                             (doseq [path missing-paths]
                               (binding [*out* *err*]
                                 (println (str "Input path does not exist: " path))))
                             (System/exit 1))
                           (let [results (mapv #(engine/process-file %
                                                                     {:mode :lint
                                                                      :config @loaded-config
                                                                      :rule-enabled? (fn [id]
                                                                                       (config/rule-enabled? @loaded-config id))})
                                               targets)
                                 kondo-findings (when-not (:no-kondo options)
                                                  (->> (kondo/findings-for-paths paths)
                                                       (config/filter-findings @loaded-config)
                                                       (config/suppress-findings @loaded-config)))
                                 results (if-not (:no-kondo options)
                                           (engine/merge-findings results kondo-findings)
                                           results)
                                 summary (engine/summarize-findings results)
                                 output (pr-str (config/baseline-data (all-findings results) summary))]
                             (write-output! (str output "\n") (:output options))))
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
                  missing-paths (fs/missing-paths paths)
                  targets (config/filter-targets @loaded-config
                                                 (fs/discover-targets paths engine-mode))
                  analyzer-facts (when (:guarded options)
                                   (analysis/analyze-paths targets))
                  _ (when-let [missing-paths (not-empty missing-paths)]
                      (doseq [path missing-paths]
                        (binding [*out* *err*]
                          (println (str "Input path does not exist: " path))))
                      (System/exit 1))
                  effective-context (assoc context
                                           :mode engine-mode
                                           :canonical? (or check-command?
                                                           (:canonical options))
                                           :guarded? (:guarded options)
                                           :analysis analyzer-facts
                                           :write? (and (not check-command?)
                                                        (not (:check options))))
                  local-results (mapv #(engine/process-file % effective-context) targets)
                  kondo-findings (when (and (= :lint mode)
                                            (not (:no-kondo options)))
                                   (->> (kondo/findings-for-paths paths)
                                        (config/filter-findings @loaded-config)
                                        (config/suppress-findings @loaded-config)))
                  results (if (and (= :lint mode)
                                   (not (:no-kondo options)))
                            (engine/merge-findings local-results kondo-findings)
                            local-results)
                  preview? (or check-command?
                               (and (= :fix mode)
                                    (:check options)))
                  results (if preview?
                            (engine/explain-format-only-changes results)
                            results)
                  report-results (if preview?
                                   (engine/preview-results results)
                                   results)
                  summary (engine/summarize-findings report-results)]
              (report-output report-results
                             summary
                             (:json options)
                             (:report @loaded-config))
              (when (or (and (= :lint mode)
                             (pos? (:findings summary)))
                        (and preview?
                             (pos? (:findings summary))))
                (System/exit 2))))))
