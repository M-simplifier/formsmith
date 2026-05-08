(ns formsmith.rules.blank-idioms-test
  (:require [clojure.test :refer [deftest is testing]]
            [formsmith.rewrite :as rewrite]))

(deftest default-fix-keeps-blank-idioms-as-suggestions
  (testing "blank idioms remain suggestions by default"
    (let [{:keys [source findings]}
          (rewrite/rewrite-string "(if (str/blank? q) true (matches-query? item q))"
                                  {:file "sample.clj" :mode :fix})]
      (is (= "(if (str/blank? q) true (matches-query? item q))" source))
      (is (= :if/blank-or (:rule-id (first findings))))
      (is (false? (:applied? (first findings)))))))

(deftest aggressive-fix-rewrites-blank-if-to-or
  (testing "if blank? true branch becomes or"
    (let [{:keys [source findings]}
          (rewrite/rewrite-string "(if (str/blank? q) true (matches-query? item q))"
                                  {:file "sample.clj" :mode :fix :aggressive? true})]
      (is (= "(or (str/blank? q) (matches-query? item q))" source))
      (is (= :if/blank-or (:rule-id (first findings))))
      (is (:applied? (first findings))))))

(deftest aggressive-fix-rewrites-seq-trim-when-to-when-not-blank
  (testing "seq(trim(or ...)) becomes when-not blank?"
    (let [{:keys [source findings]}
          (rewrite/rewrite-string "(when (seq (str/trim (or text \"\"))) (println text))"
                                  {:file "sample.clj" :mode :fix :aggressive? true})]
      (is (= "(when-not (str/blank? text) (println text))" source))
      (is (= :when/seq-trim-blank (:rule-id (first findings))))
      (is (:applied? (first findings))))))

(deftest default-fix-rewrites-empty-trim-or-to-blank
  (testing "empty? over trim(or ... \"\") is safe enough to rewrite by default"
    (let [{:keys [source findings]}
          (rewrite/rewrite-string "(empty? (str/trim (or text \"\")))"
                                  {:file "sample.clj" :mode :fix})]
      (is (= "(str/blank? text)" source))
      (is (= :empty/trim-or-blank (:rule-id (first findings))))
      (is (:applied? (first findings))))))

(deftest aggressive-fix-keeps-direct-empty-trim-as-suggestion
  (testing "direct trim calls stay suggestion-only because nil semantics can differ"
    (let [{:keys [source findings]}
          (rewrite/rewrite-string "(empty? (str/trim text))"
                                  {:file "sample.cljs" :mode :fix :aggressive? true})]
      (is (= "(empty? (str/trim text))" source))
      (is (= :empty/trim-blank (:rule-id (first findings))))
      (is (= :unsafe (:safety (first findings))))
      (is (false? (:applied? (first findings)))))))

(deftest commented-blank-idiom-stays-unchanged
  (testing "comment-bearing forms stay unchanged"
    (let [{:keys [source findings]}
          (rewrite/rewrite-string "(when (seq (str/trim (or text \"\"))) ; keep this note\n  (println text))"
                                  {:file "sample.clj" :mode :fix :aggressive? true})]
      (is (= "(when (seq (str/trim (or text \"\"))) ; keep this note\n  (println text))" source))
      (is (= :when/seq-trim-blank (:rule-id (first findings))))
      (is (false? (:applied? (first findings)))))))
