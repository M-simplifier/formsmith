(ns formsmith.analysis-test
  (:require [clojure.test :refer [deftest is testing]]
            [formsmith.analysis :as analysis]))

(deftest project-facts-include-namespace-graph
  (testing "analysis facts expose namespaces and dependencies"
    (let [facts (analysis/project-facts ["examples/cold-start-demo/src"])]
      (is (= 2 (get-in facts [:summary :namespaces])))
      (is (= 0 (get-in facts [:summary :frameworks])))
      (is (some #(= 'cold-start-demo.report (:name %))
                (:namespaces facts)))
      (is (some #(and (= 'cold-start-demo.report (:from %))
                      (= 'clojure.string (:to %))
                      (= 'str (:alias %)))
                (:namespace-deps facts))))))

(deftest project-facts-include-framework-profiles
  (testing "analysis facts expose detected framework profiles"
    (let [facts (analysis/project-facts ["examples/framework-profile-demo/src"])
          framework-ids (set (map :id (:frameworks facts)))]
      (is (= 2 (get-in facts [:summary :namespaces])))
      (is (= 6 (get-in facts [:summary :frameworks])))
      (is (= #{"integrant" "malli" "re-frame" "reagent" "reitit" "ring"}
             framework-ids)))))

(deftest local-usage-predicate-finds-function-arguments
  (testing "local usage facts can guard rewrites at a concrete source position"
    (let [analysis (analysis/analyze-paths ["examples/cold-start-demo/src/cljs/cold_start_demo/ui.cljs"])]
      (is (analysis/local-usage? analysis
                                 "examples/cold-start-demo/src/cljs/cold_start_demo/ui.cljs"
                                 'items
                                 12
                                 23)))))

(deftest var-usage-predicate-finds-core-vars
  (testing "var usage facts can prove that an operator resolves to clojure.core"
    (let [analysis (analysis/analyze-paths ["examples/cold-start-demo/src/clj/cold_start_demo/report.clj"])]
      (is (analysis/var-usage? analysis
                               "examples/cold-start-demo/src/clj/cold_start_demo/report.clj"
                               'not
                               'clojure.core
                               5
                               8)))))
