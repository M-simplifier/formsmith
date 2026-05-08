(ns formsmith.rules.and-seq-test
  (:require [clojure.test :refer [deftest is testing]]
            [formsmith.rewrite :as rewrite]))

(deftest default-fix-keeps-and-seq-as-suggestion
  (testing "boolean and+seq stays suggestion-only by default"
    (let [{:keys [source findings]}
          (rewrite/rewrite-string "(cond-> [] (and q (seq q)) (conj q))"
                                  {:file "sample.cljs" :mode :fix})]
      (is (= "(cond-> [] (and q (seq q)) (conj q))" source))
      (is (= :condition/and-seq-not-empty (:rule-id (first findings))))
      (is (false? (:applied? (first findings)))))))

(deftest aggressive-fix-rewrites-and-seq-in-cond-arrow
  (testing "boolean and+seq becomes not-empty in cond->"
    (let [{:keys [source findings]}
          (rewrite/rewrite-string "(cond-> [] (and q (seq q)) (conj q))"
                                  {:file "sample.cljs" :mode :fix :aggressive? true})]
      (is (= "(cond-> [] (not-empty q) (conj q))" source))
      (is (= :condition/and-seq-not-empty (:rule-id (first findings))))
      (is (:applied? (first findings))))))

(deftest aggressive-fix-rewrites-accessor-and-seq-in-if
  (testing "keyword accessor forms are supported in boolean tests"
    (let [{:keys [source findings]}
          (rewrite/rewrite-string "(if (and (:tags item) (seq (:tags item))) :ready :empty)"
                                  {:file "sample.cljs" :mode :fix :aggressive? true})]
      (is (= "(if (not-empty (:tags item)) :ready :empty)" source))
      (is (= :condition/and-seq-not-empty (:rule-id (first findings)))))))

(deftest aggressive-fix-rewrites-get-and-seq-in-cond-arrow
  (testing "get forms with keyword keys are supported in boolean test positions"
    (let [{:keys [source findings]}
          (rewrite/rewrite-string "(cond-> [] (and (get item :tags) (seq (get item :tags))) (conj :tags))"
                                  {:file "sample.cljs" :mode :fix :aggressive? true})]
      (is (= "(cond-> [] (not-empty (get item :tags)) (conj :tags))" source))
      (is (= :condition/and-seq-not-empty (:rule-id (first findings)))))))

(deftest aggressive-fix-rewrites-multiple-seq-clauses-in-when
  (testing "multiple seq tests in and are rewritten independently"
    (let [{:keys [source findings]}
          (rewrite/rewrite-string "(when (and (seq title) (seq goal)) (submit! title goal))"
                                  {:file "sample.cljs" :mode :fix :aggressive? true})]
      (is (= "(when (and (not-empty title) (not-empty goal)) (submit! title goal))" source))
      (is (= :condition/and-seq-not-empty (:rule-id (first findings)))))))

(deftest aggressive-fix-rewrites-seq-and-other-boolean-clauses
  (testing "seq clauses can be normalized while preserving neighboring checks"
    (let [{:keys [source findings]}
          (rewrite/rewrite-string "(when (and (seq trimmed) (not (str/starts-with? trimmed \"#\"))) (parse trimmed))"
                                  {:file "sample.clj" :mode :fix :aggressive? true})]
      (is (= "(when (and (not-empty trimmed) (not (str/starts-with? trimmed \"#\"))) (parse trimmed))" source))
      (is (= :condition/and-seq-not-empty (:rule-id (first findings)))))))

(deftest and-seq-outside-boolean-context-is-ignored
  (testing "plain and forms are ignored because they may rely on value propagation"
    (let [{:keys [source findings]}
          (rewrite/rewrite-string "(and q (seq q))"
                                  {:file "sample.cljs" :mode :fix :aggressive? true})]
      (is (= "(and q (seq q))" source))
      (is (empty? findings)))))
