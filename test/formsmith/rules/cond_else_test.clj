(ns formsmith.rules.cond-else-test
  (:require [clojure.test :refer [deftest is testing]]
            [formsmith.rewrite :as rewrite]))

(deftest rewrites-final-true-to-else
  (testing "cond catch-all uses :else"
    (let [{:keys [source findings]}
          (rewrite/rewrite-string "(cond a 1 b 2 true 3)"
                                  {:file "sample.clj" :mode :fix})]
      (is (= "(cond a 1 b 2 :else 3)" source))
      (is (= :cond/true-else (:rule-id (first findings)))))))

(deftest preserves-cond-layout-when-replacing-true
  (testing "multiline cond keeps its local layout"
    (let [{:keys [source]}
          (rewrite/rewrite-string "(cond ready? :ready\n      blocked? :blocked\n      true :waiting)"
                                  {:file "sample.clj" :mode :fix})]
      (is (= "(cond ready? :ready\n      blocked? :blocked\n      :else :waiting)"
             source)))))
