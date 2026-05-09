(ns formsmith.rules.core-idioms-test
  (:require [clojure.test :refer [deftest is testing]]
            [formsmith.rewrite :as rewrite]))

(deftest rewrites-nested-data-updates-to-thread-first
  (testing "nested assoc/update chains become a -> pipeline"
    (let [{:keys [source findings]}
          (rewrite/rewrite-string "(update (assoc user :name name) :count inc)"
                                  {:file "sample.clj" :mode :fix})]
      (is (= "(-> user (assoc :name name) (update :count inc))" source))
      (is (= [:thread/first-chain] (mapv :rule-id findings)))
      (is (:applied? (first findings))))))

(deftest rewrites-nested-sequence-transforms-to-thread-last
  (testing "nested map/filter chains become a ->> pipeline"
    (let [{:keys [source findings]}
          (rewrite/rewrite-string "(map :id (filter active? users))"
                                  {:file "sample.clj" :mode :fix})]
      (is (= "(->> users (filter active?) (map :id))" source))
      (is (= [:thread/last-chain] (mapv :rule-id findings)))
      (is (:applied? (first findings))))))

(deftest rewrites-vector-realization-to-mapv-and-filterv
  (testing "vec over map becomes mapv"
    (let [{:keys [source findings]}
          (rewrite/rewrite-string "(vec (map :id users))"
                                  {:file "sample.clj" :mode :fix})]
      (is (= "(mapv :id users)" source))
      (is (= :collection/mapv-over-vec-map (:rule-id (first findings))))
      (is (:applied? (first findings)))))
  (testing "into [] over filter becomes filterv"
    (let [{:keys [source findings]}
          (rewrite/rewrite-string "(into [] (filter active? users))"
                                  {:file "sample.clj" :mode :fix})]
      (is (= "(filterv active? users)" source))
      (is (= :collection/filterv-over-into-filter (:rule-id (first findings))))
      (is (:applied? (first findings))))))

(deftest rewrites-filter-some-map-to-keep
  (testing "filter some? over map becomes keep"
    (let [{:keys [source findings]}
          (rewrite/rewrite-string "(filter some? (map normalize users))"
                                  {:file "sample.clj" :mode :fix})]
      (is (= "(keep normalize users)" source))
      (is (= :collection/keep-over-filter-map (:rule-id (first findings))))
      (is (:applied? (first findings))))))

(deftest rewrites-assoc-current-value-to-update
  (testing "assoc of f over current keyword value becomes update"
    (let [{:keys [source findings]}
          (rewrite/rewrite-string "(assoc user :count (inc (:count user)))"
                                  {:file "sample.clj" :mode :fix})]
      (is (= "(update user :count inc)" source))
      (is (= :map/update-over-assoc (:rule-id (first findings))))
      (is (:applied? (first findings)))))
  (testing "assoc-in of f over current nested value becomes update-in"
    (let [{:keys [source findings]}
          (rewrite/rewrite-string "(assoc-in state [:ui :count] (inc (get-in state [:ui :count])))"
                                  {:file "sample.clj" :mode :fix})]
      (is (= "(update-in state [:ui :count] inc)" source))
      (is (= :map/update-in-over-assoc-in (:rule-id (first findings))))
      (is (:applied? (first findings))))))

(deftest rewrites-repeated-keyword-lookups-to-destructuring
  (testing "let bindings from one map become associative destructuring"
    (let [{:keys [source findings]}
          (rewrite/rewrite-string "(let [id (:id user) name (:name user)] [id name])"
                                  {:file "sample.clj" :mode :fix})]
      (is (= "(let [{:keys [id name]} user] [id name])" source))
      (is (= :binding/assoc-destructure (:rule-id (first findings))))
      (is (:applied? (first findings))))))

(deftest rewrites-def-fn-to-defn
  (testing "def of anonymous fn becomes defn"
    (let [{:keys [source findings]}
          (rewrite/rewrite-string "(def greet (fn [name] (str \"hi \" name)))"
                                  {:file "sample.clj" :mode :fix})]
      (is (= "(defn greet [name] (str \"hi \" name))" source))
      (is (= :defn/over-def-fn (:rule-id (first findings))))
      (is (:applied? (first findings))))))

(deftest lazy-side-effect-remains-contract
  (testing "doall over map is not mechanically rewritten"
    (let [{:keys [source findings]}
          (rewrite/rewrite-string "(doall (map send! users))"
                                  {:file "sample.clj" :mode :fix :aggressive? true})]
      (is (= "(doall (map send! users))" source))
      (is (= :lazy/doall-map-side-effect (:rule-id (first findings))))
      (is (= :unsafe (:safety (first findings))))
      (is (false? (:applied? (first findings))))
      (is (= "(run! send! users)" (:suggested-source (first findings)))))))

(deftest doto-candidate-remains-contract
  (testing "object setup let is reported but not rewritten"
    (let [input "(let [builder (StringBuilder.)] (.append builder \"a\") (.append builder \"b\") builder)"
          {:keys [source findings]}
          (rewrite/rewrite-string input
                                  {:file "sample.clj" :mode :fix :aggressive? true})]
      (is (= input source))
      (is (= :interop/doto-candidate (:rule-id (first findings))))
      (is (= :unsafe (:safety (first findings))))
      (is (false? (:applied? (first findings)))))))

(deftest comments-keep-core-idiom-rewrites-as-suggestions
  (testing "comment-sensitive forms do not apply the rewrite"
    (let [input "(update (assoc user :name name) ; keep layout\n  :count inc)"
          {:keys [source findings]}
          (rewrite/rewrite-string input
                                  {:file "sample.clj" :mode :fix})]
      (is (= input source))
      (is (= :thread/first-chain (:rule-id (first findings))))
      (is (false? (:applied? (first findings)))))))
