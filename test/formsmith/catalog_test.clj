(ns formsmith.catalog-test
  (:require [clojure.test :refer [deftest is testing]]
            [formsmith.catalog :as catalog]
            [formsmith.rule :as rule]))

(deftest finds-rule-by-id
  (testing "catalog resolves rule ids"
    (let [rule (catalog/find-rule "blank/idioms")]
      (is (= :blank/idioms (:id rule)))
      (is (= :semantic-pattern (:safety rule))))))

(deftest returns-summaries
  (testing "catalog exposes summaries for all rules"
    (let [summaries (catalog/all-rule-summaries)]
      (is (seq summaries))
      (is (some #(and (= :blank/idioms (:id %))
                      (= :analyzer-guarded-fix (:tier %)))
                summaries))
      (is (some #(and (= :if/nil-branch (:id %))
                      (= :analyzer-guarded-fix (:tier %)))
                summaries))
      (is (some #(and (= :condition/negated-form (:id %))
                      (= :standard-canonical-fix (:tier %)))
                summaries))
      (is (some #(and (= :nil/predicate (:id %))
                      (= :standard-canonical-fix (:tier %)))
                summaries))
      (is (some #(= :if/seq-test (:id %)) summaries))
      (is (some #(= :when/seq-test (:id %)) summaries)))))

(deftest certified-rules-need-known-proofs
  (testing "the rule validator rejects certified claims without a known proof"
    (let [base-rule {:id :sample/rule
                     :summary "sample"
                     :safety :syntax-safe
                     :kinds #{:rewrite}
                     :check identity
                     :apply identity}]
      (is (rule/valid-rule? (assoc base-rule
                                   :tier :certified-fix
                                   :proof :core/negated-condition)))
      (is (false? (rule/valid-rule? (assoc base-rule
                                           :tier :certified-fix
                                           :proof :bogus/proof)))))))
