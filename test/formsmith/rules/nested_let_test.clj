(ns formsmith.rules.nested-let-test
  (:require [clojure.test :refer [deftest is testing]]
            [formsmith.rewrite :as rewrite]))

(deftest flattens-nested-let
  (testing "nested let becomes one let"
    (let [{:keys [source findings]}
          (rewrite/rewrite-string "(let [a 1]\n  (let [b 2]\n    (+ a b)))"
                                  {:file "sample.clj" :mode :fix})]
      (is (= "(let [a 1 b 2]\n  (+ a b))" source))
      (is (= :let/nested-let (:rule-id (first findings))))
      (is (= 1 (:line (first findings))))
      (is (= 1 (:column (first findings)))))))

(deftest nested-let-preserves-reader-sugar-and-layout
  (testing "flattening keeps source-level forms intact"
    (let [input "(let [dispatch (rfx/use-dispatch)\n      disabled? true]\n  (let [submit! #(str % \"!\")]\n    [:button {:on-click submit!}]))"
          {:keys [source]}
          (rewrite/rewrite-string input {:file "sample.cljs" :mode :fix})]
      (is (= "(let [dispatch (rfx/use-dispatch)\n      disabled? true\n      submit! #(str % \"!\")]\n  [:button {:on-click submit!}])"
             source)))))

(deftest nested-let-lint-is-non-destructive
  (testing "lint mode reports without rewriting"
    (let [input "(let [a 1] (let [b 2] (+ a b)))"
          {:keys [source findings]}
          (rewrite/rewrite-string input {:file "sample.clj" :mode :lint})]
      (is (= input source))
      (is (= :let/nested-let (:rule-id (first findings))))
      (is (false? (:applied? (first findings)))))))
