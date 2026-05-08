(ns formsmith.rules.keyword-get-test
  (:require [clojure.test :refer [deftest is testing]]
            [formsmith.engine :as engine]))

(deftest rewrites-two-argument-get-with-keyword
  (testing "keyword lookup becomes keyword invocation"
    (let [{:keys [source findings]}
          (engine/process-source "(defn label [m] (get m :label))"
                                 {:file "sample.clj" :mode :fix})]
      (is (= "(defn label [m] (:label m))" source))
      (is (= :get/keyword-lookup (:rule-id (first findings))))
      (is (= :standard-canonical-fix (:tier (first findings)))))))

(deftest preserves-namespaced-keywords
  (testing "namespaced keywords keep their source spelling"
    (let [{:keys [source]}
          (engine/process-source "(get card ::status)"
                                 {:file "sample.clj" :mode :fix})]
      (is (= "(::status card)" source)))))

(deftest ignores-defaulted-get
  (testing "three-argument get has different missing-key semantics"
    (let [{:keys [source findings]}
          (engine/process-source "(get counts :ready 0)"
                                 {:file "sample.clj" :mode :fix})]
      (is (= "(get counts :ready 0)" source))
      (is (empty? findings)))))

(deftest keeps-comment-sensitive-lookup
  (testing "comment-sensitive get forms are report-only"
    (let [source "(get m ; target map\n  :label)"
          {:keys [source findings]} (engine/process-source source
                                                           {:file "sample.clj"
                                                            :mode :fix
                                                            :format? false})]
      (is (= "(get m ; target map\n  :label)" source))
      (is (false? (:applied? (first findings)))))))
