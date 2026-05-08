(ns formsmith.rules.redundant-str-test
  (:require [clojure.test :refer [deftest is testing]]
            [formsmith.rewrite :as rewrite]))

(deftest removes-redundant-string-literal-str
  (testing "single string literal str is flattened"
    (let [{:keys [source findings]}
          (rewrite/rewrite-string "(str \"hello\")"
                                  {:file "sample.clj" :mode :fix})]
      (is (= "\"hello\"" source))
      (is (= :str/redundant-string-literal (:rule-id (first findings)))))))

(deftest removes-redundant-string-literal-str-with-multiline-layout
  (testing "string literal source is preserved without reserializing the whole form"
    (let [{:keys [source findings]}
          (rewrite/rewrite-string "(str\n  \"hello\")"
                                  {:file "sample.clj" :mode :fix})]
      (is (= "\"hello\"" source))
      (is (= :str/redundant-string-literal (:rule-id (first findings)))))))

(deftest redundant-string-literal-str-lint-is-non-destructive
  (testing "lint mode reports without rewriting"
    (let [input "(str \"hello\")"
          {:keys [source findings]}
          (rewrite/rewrite-string input {:file "sample.clj" :mode :lint})]
      (is (= input source))
      (is (= :str/redundant-string-literal (:rule-id (first findings))))
      (is (false? (:applied? (first findings)))))))

(deftest ignores-threading-macro-step-context
  (testing "threading macro steps are not rewritten"
    (let [input "(some-> path (clojure.string/split #\"\\\\.\") last (str \".\"))"
          {:keys [source findings]}
          (rewrite/rewrite-string input {:file "sample.clj" :mode :fix})]
      (is (= input source))
      (is (empty? findings)))))
