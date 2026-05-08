(ns formsmith.rules.seq-test-test
  (:require [clojure.test :refer [deftest is testing]]
            [formsmith.rewrite :as rewrite]))

(deftest default-fix-keeps-seq-test-as-suggestion
  (testing "if seq test remains a suggestion by default"
    (let [{:keys [source findings]}
          (rewrite/rewrite-string "(if (seq prompt) prompt fallback)"
                                  {:file "sample.clj" :mode :fix})]
      (is (= "(if (seq prompt) prompt fallback)" source))
      (is (= :if/seq-not-empty-or (:rule-id (first findings))))
      (is (false? (:applied? (first findings)))))))

(deftest aggressive-fix-rewrites-self-returning-seq-test
  (testing "if returning the tested symbol becomes not-empty plus or"
    (let [{:keys [source findings]}
          (rewrite/rewrite-string "(if (seq prompt) prompt fallback)"
                                  {:file "sample.clj" :mode :fix :aggressive? true})]
      (is (= "(or (not-empty prompt) fallback)" source))
      (is (= :if/seq-not-empty-or (:rule-id (first findings))))
      (is (:applied? (first findings))))))

(deftest aggressive-fix-rewrites-general-seq-test-to-if-let
  (testing "if seq test becomes if-let with not-empty"
    (let [{:keys [source findings]}
          (rewrite/rewrite-string "(if (seq params) (vec params) [])"
                                  {:file "sample.cljs" :mode :fix :aggressive? true})]
      (is (= "(if-let [params (not-empty params)] (vec params) [])"
             source))
      (is (= :if/seq-if-let (:rule-id (first findings))))
      (is (:applied? (first findings))))))

(deftest aggressive-fix-rewrites-accessor-based-seq-test
  (testing "keyword lookup forms can become if-let with a fresh binding"
    (let [{:keys [source findings]}
          (rewrite/rewrite-string "(if (seq (:tags item)) (count (:tags item)) 0)"
                                  {:file "sample.cljs" :mode :fix :aggressive? true})]
      (is (= "(if-let [tags (not-empty (:tags item))] (count tags) 0)"
             source))
      (is (= :if/seq-if-let (:rule-id (first findings))))
      (is (:applied? (first findings))))))

(deftest aggressive-fix-rewrites-get-based-seq-test
  (testing "get forms with keyword keys can become if-let with a fresh binding"
    (let [{:keys [source findings]}
          (rewrite/rewrite-string "(if (seq (get item :tags)) (count (get item :tags)) 0)"
                                  {:file "sample.cljs" :mode :fix :aggressive? true})]
      (is (= "(if-let [tags (not-empty (:tags item))] (count tags) 0)"
             source))
      (is (= :if/seq-if-let (:rule-id (first findings)))))))

(deftest aggressive-fix-rewrites-get-in-based-seq-test
  (testing "get-in forms can use the last keyword as the binding stem"
    (let [{:keys [source findings]}
          (rewrite/rewrite-string "(if (seq (get-in item [:meta :tags])) (count (get-in item [:meta :tags])) 0)"
                                  {:file "sample.cljs" :mode :fix :aggressive? true})]
      (is (= "(if-let [tags (not-empty (get-in item [:meta :tags]))] (count tags) 0)"
             source))
      (is (= :if/seq-if-let (:rule-id (first findings)))))))

(deftest aggressive-fix-rewrites-self-returning-accessor-seq-test
  (testing "self-returning accessor forms become not-empty plus or"
    (let [{:keys [source findings]}
          (rewrite/rewrite-string "(if (seq (:tags item)) (:tags item) [])"
                                  {:file "sample.cljs" :mode :fix :aggressive? true})]
      (is (= "(or (not-empty (:tags item)) [])" source))
      (is (= :if/seq-not-empty-or (:rule-id (first findings)))))))

(deftest commented-seq-test-stays-as-suggestion
  (testing "comment-bearing forms stay unchanged"
    (let [{:keys [source findings]}
          (rewrite/rewrite-string "(if (seq prompt) prompt ; keep this note\n  fallback)"
                                  {:file "sample.clj" :mode :fix :aggressive? true})]
      (is (= "(if (seq prompt) prompt ; keep this note\n  fallback)" source))
      (is (= :if/seq-not-empty-or (:rule-id (first findings))))
      (is (false? (:applied? (first findings)))))))

(deftest metadata-bearing-seq-test-stays-as-suggestion
  (testing "metadata in rewritten branches is preserved by declining autofix"
    (let [source "(if (seq visible) (for [entry visible] ^{:key (:id entry)} [row entry]) [:p])"
          {:keys [source findings]}
          (rewrite/rewrite-string source
                                  {:file "sample.cljs"
                                   :mode :fix
                                   :aggressive? true})]
      (is (= "(if (seq visible) (for [entry visible] ^{:key (:id entry)} [row entry]) [:p])"
             source))
      (is (= :if/seq-if-let (:rule-id (first findings))))
      (is (false? (:applied? (first findings)))))))
