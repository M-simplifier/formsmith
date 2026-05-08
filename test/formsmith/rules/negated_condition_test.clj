(ns formsmith.rules.negated-condition-test
  (:require [clojure.test :refer [deftest is testing]]
            [formsmith.rewrite :as rewrite]))

(deftest rewrites-when-not-condition
  (testing "unqualified when with not still rewrites as a standard canonical fix"
    (let [{:keys [source findings]}
          (rewrite/rewrite-string "(when (not ready?) (println :x))"
                                  {:file "sample.clj" :mode :fix})]
      (is (= "(when-not ready? (println :x))" source))
      (is (= :when/not-condition (:rule-id (first findings))))
      (is (= :standard-canonical-fix (:tier (first findings))))
      (is (:applied? (first findings))))))

(deftest rewrites-if-not-condition
  (testing "fully qualified core not can be certified and rewritten"
    (let [{:keys [source findings]}
          (rewrite/rewrite-string "(if (clojure.core/not ready?) :pending :ready)"
                                  {:file "sample.clj" :mode :fix})]
      (is (= "(clojure.core/if-not ready? :pending :ready)" source))
      (is (= :if/not-condition (:rule-id (first findings))))
      (is (= :certified-fix (:tier (first findings))))
      (is (= :core/negated-condition (:proof (first findings))))
      (is (:applied? (first findings))))))

(deftest local-not-binding-is-not-rewritten
  (testing "local not bindings block the syntactic canonical rewrite"
    (let [{:keys [source findings]}
          (rewrite/rewrite-string "(fn [not ready?] (if (not ready?) :pending :ready))"
                                  {:file "sample.clj" :mode :fix})]
      (is (= "(fn [not ready?] (if (not ready?) :pending :ready))" source))
      (is (empty? findings)))))

(deftest local-replacement-binding-is-not-rewritten
  (testing "local if-not bindings block rewrites that would target if-not"
    (let [{:keys [source findings]}
          (rewrite/rewrite-string "(fn [if-not ready?] (if (not ready?) :pending :ready))"
                                  {:file "sample.clj" :mode :fix})]
      (is (= "(fn [if-not ready?] (if (not ready?) :pending :ready))" source))
      (is (empty? findings)))))

(deftest letfn-local-not-is-not-rewritten
  (testing "letfn names block unqualified not rewrites"
    (let [{:keys [source findings]}
          (rewrite/rewrite-string "(letfn [(not [x] false)] (if (not ready?) :pending :ready))"
                                  {:file "sample.clj" :mode :fix})]
      (is (= "(letfn [(not [x] false)] (if (not ready?) :pending :ready))" source))
      (is (empty? findings)))))

(deftest letfn-local-replacement-is-not-rewritten
  (testing "letfn names block unqualified if-not replacements"
    (let [{:keys [source findings]}
          (rewrite/rewrite-string "(letfn [(if-not [x t f] t)] (if (not ready?) :pending :ready))"
                                  {:file "sample.clj" :mode :fix})]
      (is (= "(letfn [(if-not [x t f] t)] (if (not ready?) :pending :ready))" source))
      (is (empty? findings)))))

(deftest if-some-local-not-is-not-rewritten
  (testing "if-some bindings block unqualified not rewrites"
    (let [{:keys [source findings]}
          (rewrite/rewrite-string "(if-some [not f] (if (not ready?) :pending :ready) :missing)"
                                  {:file "sample.clj" :mode :fix})]
      (is (= "(if-some [not f] (if (not ready?) :pending :ready) :missing)" source))
      (is (empty? findings)))))

(deftest comprehension-let-local-not-is-not-rewritten
  (testing "for :let bindings block unqualified not rewrites"
    (let [{:keys [source findings]}
          (rewrite/rewrite-string "(for [x xs :let [not f]] (if (not x) :a :b))"
                                  {:file "sample.clj" :mode :fix})]
      (is (= "(for [x xs :let [not f]] (if (not x) :a :b))" source))
      (is (empty? findings)))))

(deftest comprehension-let-local-replacement-is-not-rewritten
  (testing "doseq :let bindings block unqualified when-not replacements"
    (let [{:keys [source findings]}
          (rewrite/rewrite-string "(doseq [x xs :let [when-not f]] (when (not x) (println x)))"
                                  {:file "sample.clj" :mode :fix})]
      (is (= "(doseq [x xs :let [when-not f]] (when (not x) (println x)))" source))
      (is (empty? findings)))))

(deftest rewrites-fully-qualified-when-not-condition
  (testing "fully qualified core when/not can be certified and rewritten"
    (let [{:keys [source findings]}
          (rewrite/rewrite-string "(clojure.core/when (clojure.core/not ready?) (println :x))"
                                  {:file "sample.clj" :mode :fix})]
      (is (= "(clojure.core/when-not ready? (println :x))" source))
      (is (= :when/not-condition (:rule-id (first findings))))
      (is (= :certified-fix (:tier (first findings))))
      (is (= :core/negated-condition (:proof (first findings))))
      (is (:applied? (first findings))))))

(deftest commented-negated-condition-stays-as-suggestion
  (testing "comment-bearing forms stay unchanged for safety"
    (let [{:keys [source findings]}
          (rewrite/rewrite-string "(when (not ready?) ; keep this note\n  (println :x))"
                                  {:file "sample.clj" :mode :fix})]
      (is (= "(when (not ready?) ; keep this note\n  (println :x))" source))
      (is (= :when/not-condition (:rule-id (first findings))))
      (is (false? (:applied? (first findings)))))))
