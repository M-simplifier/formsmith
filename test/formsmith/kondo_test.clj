(ns formsmith.kondo-test
  (:require [clojure.test :refer [deftest is testing]]
            [formsmith.kondo :as kondo]))

(deftest normalizes-kondo-findings
  (testing "clj-kondo findings are projected into formsmith's finding model"
    (let [findings (kondo/normalize-findings
                    {:findings [{:type :unresolved-symbol
                                 :level :error
                                 :filename "sample.clj"
                                 :row 1
                                 :col 2
                                 :message "Unresolved symbol: foo"}]})
          finding (first findings)]
      (is (= 1 (count findings)))
      (is (= :kondo/unresolved-symbol (:rule-id finding)))
      (is (= :error (:severity finding)))
      (is (= :clj-kondo (:source finding))))))
