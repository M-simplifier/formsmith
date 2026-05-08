(ns formsmith.rules.empty-let-test
  (:require [clojure.test :refer [deftest is testing]]
            [formsmith.rewrite :as rewrite]))

(deftest removes-empty-let-around-single-body
  (testing "empty let around one body expression is removed"
    (let [{:keys [source findings]}
          (rewrite/rewrite-string "(let [] (println :x))"
                                  {:file "sample.clj" :mode :fix})]
      (is (= "(println :x)" source))
      (is (= :let/empty-bindings (:rule-id (first findings)))))))

(deftest removes-empty-let-around-multiple-bodies
  (testing "empty let around multiple expressions becomes do"
    (let [{:keys [source findings]}
          (rewrite/rewrite-string "(let [] (println :x) (println :y))"
                                  {:file "sample.clj" :mode :fix})]
      (is (= "(do (println :x) (println :y))" source))
      (is (= :let/empty-bindings (:rule-id (first findings)))))))

(deftest preserves-multiline-body-when-removing-empty-let
  (testing "single-body multiline forms keep local layout"
    (let [{:keys [source]}
          (rewrite/rewrite-string "(let []\n  (when ok?\n    (println :x)))"
                                  {:file "sample.clj" :mode :fix})]
      (is (= "(when ok?\n  (println :x))" source)))))

(deftest preserves-body-layout-when-empty-let-becomes-do
  (testing "multiple bodies keep their block layout"
    (let [{:keys [source]}
          (rewrite/rewrite-string "(let []\n  (println :x)\n  (println :y))"
                                  {:file "sample.clj" :mode :fix})]
      (is (= "(do\n  (println :x)\n  (println :y))" source)))))
