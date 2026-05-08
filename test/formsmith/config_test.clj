(ns formsmith.config-test
  (:require [clojure.test :refer [deftest is testing]]
            [formsmith.config :as config]))

(deftest rule-selection-normalizes-ids
  (testing "rule selectors accept public string ids"
    (let [cfg (config/normalize {:rules {:only ["if/not-condition"
                                                :cond/true-else]
                                         :exclude ["cond/true-else"]}})]
      (is (config/rule-enabled? cfg :if/not-condition))
      (is (not (config/rule-enabled? cfg :cond/true-else)))
      (is (not (config/rule-enabled? cfg :let/nested-let))))))

(deftest ignore-paths-filter-targets-and-findings
  (testing "ignored roots are removed from user-facing work"
    (let [cfg (config/normalize {:ignore-paths ["corpus" "target/generated"]})]
      (is (= ["src/app/core.clj"]
             (config/filter-targets cfg
                                    ["src/app/core.clj"
                                     "corpus/basic/bad.before.clj"
                                     "target/generated/app.clj"])))
      (is (= [{:file "src/app/core.clj" :rule-id :a}]
             (config/filter-findings cfg
                                     [{:file "src/app/core.clj" :rule-id :a}
                                      {:file "corpus/basic/bad.before.clj" :rule-id :b}]))))))

(deftest inline-suppressions-cover-file-line-and-rule
  (testing "inline comments create production-friendly suppressions"
    (let [source ";; formsmith-disable-next-line if/not-condition\n(if (not ok?) :a :b)\n(when (not ok?) :x) ;; formsmith-disable-line\n"
          suppressions (config/inline-suppressions "src/app/core.clj" source)]
      (is (config/suppressed? suppressions
                              {:file "src/app/core.clj"
                               :rule-id :if/not-condition
                               :line 2
                               :column 1}))
      (is (not (config/suppressed? suppressions
                                   {:file "src/app/core.clj"
                                    :rule-id :cond/true-else
                                    :line 2
                                    :column 1})))
      (is (config/suppressed? suppressions
                              {:file "src/app/core.clj"
                               :rule-id :any/rule
                               :line 3
                               :column 1})))))

(deftest baseline-data-is-reusable-as-suppression-input
  (testing "baseline entries suppress matching existing findings"
    (let [finding {:file "src/app/core.clj"
                   :rule-id :if/not-condition
                   :line 10
                   :column 3
                   :message "use if-not"
                   :tier :standard-canonical-fix}
          baseline (config/baseline-data [finding] {:files 1 :changed 0 :findings 1})
          suppression (-> baseline :findings first config/normalize-suppression)]
      (is (= :if/not-condition (:rule-id suppression)))
      (is (config/suppression-matches? suppression finding)))))
