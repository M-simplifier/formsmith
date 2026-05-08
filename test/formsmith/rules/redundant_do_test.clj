(ns formsmith.rules.redundant-do-test
  (:require [clojure.test :refer [deftest is testing]]
            [formsmith.rewrite :as rewrite]))

(deftest flattens-when-do
  (testing "when with a single do body is flattened"
    (let [{:keys [source findings]}
          (rewrite/rewrite-string "(when ok? (do (println :a) (println :b)))"
                                  {:file "sample.clj" :mode :fix})]
      (is (= "(when ok? (println :a) (println :b))" source))
      (is (= 1 (count findings)))
      (is (= :redundant-do/body (:rule-id (first findings)))))))

(deftest flattens-general-redundant-do
  (testing "let body with redundant do is flattened"
    (let [{:keys [source findings]}
          (rewrite/rewrite-string "(let [x 1] (do (inc x) (prn x)))"
                                  {:file "sample.clj" :mode :fix})]
      (is (= "(let [x 1] (inc x) (prn x))" source))
      (is (= 1 (count findings)))
      (is (= :redundant-do/body (:rule-id (first findings)))))))

(deftest preserves-body-layout-when-removing-redundant-do
  (testing "multiline when keeps local body layout"
    (let [{:keys [source]}
          (rewrite/rewrite-string "(when ok?\n  (do\n    (println :a)\n    (println :b)))"
                                  {:file "sample.clj" :mode :fix})]
      (is (= "(when ok?\n  (println :a)\n  (println :b))" source)))))

(deftest lint-mode-does-not-rewrite
  (testing "lint mode reports but does not change source"
    (let [{:keys [source findings]}
          (rewrite/rewrite-string "(when ok? (do (println :a) (println :b)))"
                                  {:file "sample.clj" :mode :lint})]
      (is (= "(when ok? (do (println :a) (println :b)))" source))
      (is (= 1 (count findings)))
      (is (false? (:applied? (first findings)))))))

(deftest fix-mode-skips-comment-sensitive-form
  (testing "fix mode leaves commented forms unchanged for safety"
    (let [input "(when ok? ; keep this note\n  (do (println :a) (println :b)))"
          {:keys [source findings]}
          (rewrite/rewrite-string input {:file "sample.clj" :mode :fix})]
      (is (= input source))
      (is (= 1 (count findings)))
      (is (false? (:applied? (first findings)))))))
