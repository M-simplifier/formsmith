(ns formsmith.rules.namespace-hygiene-test
  (:require [clojure.test :refer [deftest is testing]]
            [formsmith.engine :as engine]))

(deftest emits-canonical-alias-contract
  (testing "known namespaces receive canonical alias contracts"
    (let [{:keys [findings]}
          (engine/process-source
           "(ns demo.core
  (:require [clojure.string :as string]))
"
           {:file "src/demo/core.clj"
            :mode :lint})]
      (is (= [:namespace/canonical-alias] (mapv :rule-id findings)))
      (is (false? (:applied? (first findings))))
      (is (= "[clojure.string :as str]" (:suggested-source (first findings)))))))

(deftest emits-refer-and-use-contracts
  (testing "refer-all is a namespace hygiene contract"
    (let [{:keys [findings]}
          (engine/process-source
           "(ns demo.core
  (:require [demo.util :refer :all]))
"
           {:file "src/demo/core.clj"
            :mode :lint})]
      (is (= [:namespace/refer-all] (mapv :rule-id findings)))))
  (testing "use clauses are a namespace hygiene contract"
    (let [{:keys [findings]}
          (engine/process-source
           "(ns demo.core
  (:use demo.util))
"
           {:file "src/demo/core.clj"
            :mode :lint})]
      (is (= [:namespace/use-clause] (mapv :rule-id findings))))))

(deftest emits-duplicate-require-contract
  (testing "duplicate require entries are reported once at the ns form"
    (let [{:keys [findings]}
          (engine/process-source
           "(ns demo.core
  (:require [demo.a :as a]
            [demo.a :as a2]))
"
           {:file "src/demo/core.clj"
            :mode :lint})]
      (is (= [:namespace/duplicate-require] (mapv :rule-id findings))))))

(deftest emits-source-path-contract-only-for-source-trees
  (testing "src/test namespaces should line up with the file path"
    (let [{:keys [findings]}
          (engine/process-source
           "(ns demo.not-core)"
           {:file "src/demo/core.clj"
            :mode :lint})]
      (is (= [:namespace/file-path-mismatch] (mapv :rule-id findings)))))
  (testing "fixture paths are ignored for namespace/file-path-mismatch"
    (let [{:keys [findings]}
          (engine/process-source
           "(ns demo.not-core)"
           {:file "corpus/basic/sample.before.clj"
            :mode :lint})]
      (is (empty? findings)))))
