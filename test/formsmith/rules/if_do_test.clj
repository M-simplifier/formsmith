(ns formsmith.rules.if-do-test
  (:require [clojure.test :refer [deftest is testing]]
            [formsmith.rewrite :as rewrite]))

(deftest flattens-single-expression-do-in-if
  (testing "if branches with a single-expression do are flattened"
    (let [{:keys [source findings]}
          (rewrite/rewrite-string "(if ok? (do (inc x)) (do (dec x)))"
                                  {:file "sample.clj" :mode :fix})]
      (is (= "(if ok? (inc x) (dec x))" source))
      (is (= 1 (count findings)))
      (is (= :if/redundant-do-branch (:rule-id (first findings)))))))

(deftest preserves-if-layout-while-unwrapping-do
  (testing "multiline if keeps branch layout after rewrite"
    (let [{:keys [source]}
          (rewrite/rewrite-string "(if ok?\n  (do\n    (println :x))\n  (do\n    (println :y)))"
                                  {:file "sample.clj" :mode :fix})]
      (is (= "(if ok?\n  (println :x)\n  (println :y))"
             source)))))
