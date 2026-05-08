(ns formsmith.contract-test
  (:require [clojure.java.io :as io]
            [clojure.test :refer [deftest is testing]]
            [formsmith.contract :as contract]
            [formsmith.engine :as engine]
            [formsmith.fs :as fs]))

(deftest builds-contracts-for-llm-refactor-findings
  (testing "unsafe findings become structured LLM refactor contracts"
    (let [{:keys [findings]}
          (engine/process-source "(empty? (str/trim q))\n"
                                 {:file "sample.clj" :mode :lint})
          contracts (contract/contracts-from-results [{:file "sample.clj"
                                                       :findings findings}])
          first-contract (first contracts)]
      (is (= 1 (count contracts)))
      (is (= "llm-refactor" (:type first-contract)))
      (is (= "empty/trim-blank" (:rule-id first-contract)))
      (is (= "(str/blank? q)" (:suggested-source first-contract)))
      (is (seq (:blocked-by first-contract)))
      (is (seq (:acceptance first-contract))))))

(deftest contract-paths-are-display-relative
  (testing "external-facing contracts avoid absolute local paths when possible"
    (let [absolute-path (.getPath (.getCanonicalFile (io/file "sample.clj")))
          contract (contract/finding->contract
                    {:rule-id :empty/trim-blank
                     :message "message"
                     :tier :llm-refactor
                     :safety :unsafe
                     :source :formsmith
                     :file absolute-path
                     :line 1
                     :column 1
                     :before "(empty? (str/trim q))"
                     :suggested-source "(str/blank? q)"})]
      (is (= "sample.clj" (get-in contract [:location :file])))
      (is (.contains (:id contract) "sample.clj"))
      (is (not (.contains (:id contract) absolute-path))))))

(deftest display-paths-are-relative-when-possible
  (testing "shared display paths avoid leaking absolute local roots"
    (let [absolute-path (.getPath (.getCanonicalFile (io/file "sample.clj")))]
      (is (= "sample.clj" (fs/display-path absolute-path))))))

(deftest ignores-standard-canonical-findings
  (testing "mechanical canonical findings are not LLM contracts"
    (let [{:keys [findings]}
          (engine/process-source "(when (not ready?) (println :x))\n"
                                 {:file "sample.clj" :mode :lint})]
      (is (empty? (contract/contracts-from-results [{:file "sample.clj"
                                                     :findings findings}]))))))
