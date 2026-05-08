(ns formsmith.rules.cond-minimal-test
  (:require [clojure.test :refer [deftest is testing]]
            [formsmith.rewrite :as rewrite]))

(deftest rewrites-single-clause-cond-to-when
  (testing "single-clause cond becomes when"
    (let [{:keys [source findings]}
          (rewrite/rewrite-string "(cond ready? :ready)"
                                  {:file "sample.clj" :mode :fix})]
      (is (= "(when ready? :ready)" source))
      (is (= :cond/single-clause-when (:rule-id (first findings)))))))

(deftest rewrites-two-clause-cond-to-if
  (testing "two-clause cond with catch-all becomes if"
    (let [{:keys [source findings]}
          (rewrite/rewrite-string "(cond ready? :ready :else :waiting)"
                                  {:file "sample.clj" :mode :fix})]
      (is (= "(if ready? :ready :waiting)" source))
      (is (= :cond/two-clause-if (:rule-id (first findings)))))))

(deftest preserves-multiline-layout-when-cond-becomes-when
  (testing "single-clause cond keeps its local multiline layout in rewrite-only mode"
    (let [{:keys [source findings]}
          (rewrite/rewrite-string "(cond\n  ready?\n  :ready)"
                                  {:file "sample.clj" :mode :fix :format? false})]
      (is (= "(when\n  ready?\n  :ready)" source))
      (is (= :cond/single-clause-when (:rule-id (first findings)))))))

(deftest preserves-multiline-layout-when-cond-becomes-if
  (testing "two-clause cond keeps its local multiline layout in rewrite-only mode"
    (let [{:keys [source findings]}
          (rewrite/rewrite-string "(cond\n  ready?\n  :ready\n  :else\n  :waiting)"
                                  {:file "sample.clj" :mode :fix :format? false})]
      (is (= "(if\n  ready?\n  :ready\n  :waiting)" source))
      (is (= :cond/two-clause-if (:rule-id (first findings)))))))

(deftest preserves-line-break-before-inline-catchall-else-expression
  (testing "two-clause cond reuses the break before :else, not only spaces after :else"
    (let [{:keys [source findings]}
          (rewrite/rewrite-string "(cond \n  (> n 0) (println :positive)\n  :else  (println :zero))"
                                  {:file "sample.clj" :mode :fix :format? false})]
      (is (= "(if\n  (> n 0) (println :positive)\n  (println :zero))" source))
      (is (= :cond/two-clause-if (:rule-id (first findings)))))))
