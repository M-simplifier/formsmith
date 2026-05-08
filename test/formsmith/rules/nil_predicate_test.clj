(ns formsmith.rules.nil-predicate-test
  (:require [clojure.test :refer [deftest is testing]]
            [formsmith.engine :as engine]))

(deftest rewrites-nil-equality
  (testing "nil equality becomes nil?"
    (let [{:keys [source findings]}
          (engine/process-source "(if (= nil user) :missing user)"
                                 {:file "sample.clj" :mode :fix})]
      (is (= "(if (nil? user) :missing user)" source))
      (is (= :nil/eq (:rule-id (first findings))))
      (is (= :standard-canonical-fix (:tier (first findings)))))))

(deftest rewrites-nil-inequality
  (testing "nil inequality becomes some?"
    (let [{:keys [source findings]}
          (engine/process-source "(when (not= token nil) (submit token))"
                                 {:file "sample.clj" :mode :fix})]
      (is (= "(when (some? token) (submit token))" source))
      (is (= :nil/not-eq (:rule-id (first findings)))))))

(deftest keeps-comment-sensitive-comparison
  (testing "comment-sensitive forms are report-only"
    (let [source "(if (= nil user) ; generated shape\n  :missing\n  user)"
          {:keys [source findings]} (engine/process-source source
                                                           {:file "sample.clj"
                                                            :mode :fix})]
      (is (= "(if (= nil user) ; generated shape\n  :missing\n  user)" source))
      (is (false? (:applied? (first findings)))))))
