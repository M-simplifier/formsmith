(ns formsmith.rules.if-nil-test
  (:require [clojure.test :refer [deftest is testing]]
            [formsmith.rewrite :as rewrite]))

(deftest default-fix-keeps-semantic-pattern-as-suggestion
  (testing "if with nil else is reported but not rewritten by default"
    (let [{:keys [source findings]}
          (rewrite/rewrite-string "(if ok? (println :x) nil)"
                                  {:file "sample.clj" :mode :fix})]
      (is (= "(if ok? (println :x) nil)" source))
      (is (= :if/nil-else-branch (:rule-id (first findings))))
      (is (false? (:applied? (first findings)))))))

(deftest aggressive-fix-rewrites-if-nil-else-to-when
  (testing "if with nil else becomes when in aggressive mode"
    (let [{:keys [source findings]}
          (rewrite/rewrite-string "(if ok? (println :x) nil)"
                                  {:file "sample.clj" :mode :fix :aggressive? true})]
      (is (= "(when ok? (println :x))" source))
      (is (= :if/nil-else-branch (:rule-id (first findings))))
      (is (:applied? (first findings))))))

(deftest aggressive-fix-rewrites-if-nil-then-to-when-not
  (testing "if with nil then becomes when-not in aggressive mode"
    (let [{:keys [source findings]}
          (rewrite/rewrite-string "(if ok? nil (println :x))"
                                  {:file "sample.clj" :mode :fix :aggressive? true})]
      (is (= "(when-not ok? (println :x))" source))
      (is (= :if/nil-then-branch (:rule-id (first findings)))))))
