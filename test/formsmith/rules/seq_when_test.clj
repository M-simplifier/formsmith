(ns formsmith.rules.seq-when-test
  (:require [clojure.test :refer [deftest is testing]]
            [formsmith.rewrite :as rewrite]))

(deftest default-fix-keeps-when-seq-test-as-suggestion
  (testing "when seq test remains a suggestion by default"
    (let [{:keys [source findings]}
          (rewrite/rewrite-string "(when (seq users) (rand-nth users))"
                                  {:file "sample.clj" :mode :fix})]
      (is (= "(when (seq users) (rand-nth users))" source))
      (is (= :when/seq-when-let (:rule-id (first findings))))
      (is (false? (:applied? (first findings)))))))

(deftest aggressive-fix-rewrites-when-seq-test
  (testing "when seq test becomes when-let with not-empty"
    (let [{:keys [source findings]}
          (rewrite/rewrite-string "(when (seq users) (rand-nth users))"
                                  {:file "sample.clj" :mode :fix :aggressive? true})]
      (is (= "(when-let [users (not-empty users)] (rand-nth users))" source))
      (is (= :when/seq-when-let (:rule-id (first findings))))
      (is (:applied? (first findings))))))

(deftest aggressive-fix-rewrites-multi-body-when-seq-test
  (testing "multiple body forms are preserved"
    (let [{:keys [source findings]}
          (rewrite/rewrite-string "(when (seq posts) (println :x) (count posts))"
                                  {:file "sample.clj" :mode :fix :aggressive? true})]
      (is (= "(when-let [posts (not-empty posts)] (println :x) (count posts))" source))
      (is (= :when/seq-when-let (:rule-id (first findings)))))))

(deftest aggressive-fix-rewrites-accessor-based-when-seq-test
  (testing "keyword lookup forms can become when-let with a fresh binding"
    (let [{:keys [source findings]}
          (rewrite/rewrite-string "(when (seq (:tags item)) [tag-list (:tags item)])"
                                  {:file "sample.cljs" :mode :fix :aggressive? true})]
      (is (= "(when-let [tags (not-empty (:tags item))] [tag-list tags])" source))
      (is (= :when/seq-when-let (:rule-id (first findings))))
      (is (:applied? (first findings))))))

(deftest aggressive-fix-avoids-shadowing-existing-symbols
  (testing "fresh bindings avoid colliding with names already used in the body"
    (let [{:keys [source findings]}
          (rewrite/rewrite-string "(when (seq (:tags item)) (let [tags 1] [tags (:tags item)]))"
                                  {:file "sample.cljs" :mode :fix :aggressive? true})]
      (is (= "(when-let [tags-value (not-empty (:tags item))] (let [tags 1] [tags tags-value]))"
             source))
      (is (= :when/seq-when-let (:rule-id (first findings)))))))

(deftest aggressive-fix-rewrites-get-in-based-when-seq-test
  (testing "get-in forms can become when-let with a derived binding name"
    (let [{:keys [source findings]}
          (rewrite/rewrite-string "(when (seq (get-in item [:meta :tags])) [tag-list (get-in item [:meta :tags])])"
                                  {:file "sample.cljs" :mode :fix :aggressive? true})]
      (is (= "(when-let [tags (not-empty (get-in item [:meta :tags]))] [tag-list tags])"
             source))
      (is (= :when/seq-when-let (:rule-id (first findings)))))))
(deftest seq-when-constant-body-is-ignored
  (testing "constant bodies do not introduce pointless when-let bindings"
    (let [{:keys [source findings]}
          (rewrite/rewrite-string "(when (seq results) \"\\n\")"
                                  {:file "sample.clj" :mode :fix :aggressive? true})]
      (is (= "(when (seq results) \"\\n\")" source))
      (is (empty? findings)))))
