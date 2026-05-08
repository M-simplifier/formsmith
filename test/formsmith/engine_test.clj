(ns formsmith.engine-test
  (:require [clojure.test :refer [deftest is testing]]
            [formsmith.analysis :as analysis]
            [formsmith.engine :as engine]))

(deftest process-source-formats-after-rewrite
  (testing "semantic rewrite happens before formatting"
    (let [{:keys [source findings]}
          (engine/process-source "(when ok?\n(do\n(println :a)\n(println :b)))\n"
                                 {:file "sample.clj" :mode :fix})]
      (is (= "(when ok?\n  (println :a)\n  (println :b))\n" source))
      (is (= 1 (count findings)))
      (is (= :standard-canonical-fix (:tier (first findings)))))))

(deftest process-source-attaches-certified-proof
  (testing "certified rules carry proof metadata on findings"
    (let [{:keys [source findings]}
          (engine/process-source "(if (clojure.core/not ok?) :a :b)\n"
                                 {:file "sample.clj" :mode :fix})]
      (is (= "(clojure.core/if-not ok? :a :b)\n" source))
      (is (= 1 (count findings)))
      (is (= :certified-fix (:tier (first findings))))
      (is (= :core/negated-condition (:proof (first findings)))))))

(deftest process-file-guarded-certifies-core-negated-condition
  (testing "guarded mode certifies unqualified core not through analyzer facts"
    (let [tmp-file (doto (java.io.File/createTempFile "formsmith-certified" ".clj")
                     (.deleteOnExit))
          source "(ns certified.sample)\n(defn f [x]\n  (if (not x) :a :b))\n"]
      (spit tmp-file source)
      (let [path (.getPath tmp-file)
            analysis (analysis/analyze-paths [path])
            result (engine/process-file path
                                        {:mode :fix
                                         :guarded? true
                                         :analysis analysis
                                         :write? false})]
        (is (:changed? result))
        (is (= source (slurp tmp-file)))
        (is (.contains (:source result) "(clojure.core/if-not x :a :b)"))
        (is (= :certified-fix (:tier (first (:findings result)))))
        (is (= :core/negated-condition (:proof (first (:findings result)))))))))

(deftest process-source-format-mode-does-not-rewrite
  (testing "format mode preserves semantic shape"
    (let [{:keys [source findings]}
          (engine/process-source "(when ok?\n(do\n(println :a)\n(println :b)))\n"
                                 {:file "sample.clj" :mode :format})]
      (is (= "(when ok?\n  (do\n    (println :a)\n    (println :b)))\n" source))
      (is (.contains source "(do"))
      (is (empty? findings)))))

(deftest process-source-fix-can-skip-formatting
  (testing "fix mode can apply rewrites without full-file formatting"
    (let [{:keys [source findings]}
          (engine/process-source "(let [a 1]\n  (let [b 2]\n    (+ a b)))\n"
                                 {:file "sample.clj" :mode :fix :format? false})]
      (is (= "(let [a 1 b 2]\n  (+ a b))\n" source))
      (is (= 1 (count findings))))))

(deftest process-source-fix-default-keeps-semantic-pattern-as-suggestion
  (testing "default fix mode does not auto-apply semantic-pattern rewrites"
    (let [{:keys [source findings]}
          (engine/process-source "(if ok? (println :x) nil)\n"
                                 {:file "sample.clj" :mode :fix})]
      (is (= "(if ok? (println :x) nil)\n" source))
      (is (= 1 (count findings)))
      (is (= :analyzer-guarded-fix (:tier (first findings))))
      (is (false? (:applied? (first findings)))))))

(deftest process-source-fix-aggressive-applies-semantic-pattern
  (testing "aggressive fix mode applies semantic-pattern rewrites"
    (let [{:keys [source findings]}
          (engine/process-source "(if ok? (println :x) nil)\n"
                                 {:file "sample.clj" :mode :fix :aggressive? true})]
      (is (= "(when ok? (println :x))\n" source))
      (is (= 1 (count findings)))
      (is (:applied? (first findings))))))

(deftest process-file-guarded-applies-local-seq-rewrite
  (testing "guarded mode applies analyzer-backed seq rewrites for local symbols"
    (let [tmp-file (doto (java.io.File/createTempFile "formsmith-guarded" ".clj")
                     (.deleteOnExit))
          source "(ns guarded.sample)\n(defn f [xs]\n  (if (seq xs) (count xs) 0))\n"]
      (spit tmp-file source)
      (let [path (.getPath tmp-file)
            analysis (analysis/analyze-paths [path])
            result (engine/process-file path
                                        {:mode :fix
                                         :guarded? true
                                         :analysis analysis
                                         :write? false})]
        (is (:changed? result))
        (is (= source (slurp tmp-file)))
        (is (.contains (:source result) "(if-let [xs (not-empty xs)]"))
        (is (= :analyzer-guarded-fix (:tier (first (:findings result)))))
        (is (:applied? (first (:findings result))))))))

(deftest process-source-lint-mode-does-not-format
  (testing "lint mode reports without changing the source"
    (let [source "(when  ok?   (do  (println :a)   (println :b)))\n"
          result (engine/process-source source {:file "sample.clj" :mode :lint})]
      (is (= source (:source result)))
      (is (= 1 (count (:findings result))))
      (is (false? (:applied? (first (:findings result))))))))

(deftest process-file-check-mode-does-not-write
  (testing "process-file can report changes without writing them"
    (let [tmp-file (doto (java.io.File/createTempFile "formsmith-engine" ".clj")
                     (.deleteOnExit))
          source "(if ok? (println :x) nil)\n"]
      (spit tmp-file source)
      (let [result (engine/process-file (.getPath tmp-file)
                                        {:mode :fix
                                         :aggressive? true
                                         :write? false})]
        (is (:changed? result))
        (is (= source (slurp tmp-file)))
        (is (= "(when ok? (println :x))\n" (:source result)))))))

(deftest preview-results-keep-only-applied-findings
  (testing "preview mode should show only findings that would actually apply"
    (let [results [{:file "sample.clj"
                    :changed? true
                    :findings [{:rule-id :a :applied? true}
                               {:rule-id :b :applied? false}]}
                   {:file "other.clj"
                    :changed? false
                    :findings [{:rule-id :c :applied? false}]}]
          preview (engine/preview-results results)]
      (is (= [{:file "sample.clj"
               :changed? true
               :findings [{:rule-id :a :applied? true}]}
              {:file "other.clj"
               :changed? false
               :findings []}]
             preview))
      (is (= {:files 2 :changed 1 :findings 1}
             (engine/summarize-findings preview))))))

(deftest merge-findings-suppresses-known-kondo-overlaps
  (testing "known formsmith rewrites suppress duplicate kondo findings"
    (let [results [{:file "sample.clj"
                    :changed? false
                    :findings [{:rule-id :str/redundant-string-literal
                                :line 10
                                :column 4}
                               {:rule-id :let/nested-let
                                :line 20
                                :column 2}]}]
          kondo-findings [{:file "sample.clj"
                           :rule-id :kondo/redundant-str-call
                           :line 10
                           :column 4}
                          {:file "sample.clj"
                           :rule-id :kondo/redundant-let
                           :line 24
                           :column 5}
                          {:file "sample.clj"
                           :rule-id :kondo/unresolved-symbol
                           :line 40
                           :column 1}]
          merged (engine/merge-findings results kondo-findings)
          findings (:findings (first merged))]
      (is (= 3 (count findings)))
      (is (some #(= :str/redundant-string-literal (:rule-id %)) findings))
      (is (some #(= :let/nested-let (:rule-id %)) findings))
      (is (some #(= :kondo/unresolved-symbol (:rule-id %)) findings))
      (is (not-any? #(= :kondo/redundant-str-call (:rule-id %)) findings))
      (is (not-any? #(= :kondo/redundant-let (:rule-id %)) findings)))))
