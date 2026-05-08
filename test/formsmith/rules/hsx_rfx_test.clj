(ns formsmith.rules.hsx-rfx-test
  (:require [clojure.test :refer [deftest is testing]]
            [formsmith.contract :as contract]
            [formsmith.engine :as engine]))

(deftest emits-hsx-local-state-contract
  (testing "HSX code receives a contract for Reagent ratom local state"
    (let [{:keys [findings]}
          (engine/process-source
           "(ns demo.hsx
  (:require [io.factorhouse.hsx.core :as hsx]
            [reagent.core :as r]))
(defn counter []
  (let [n (r/atom 0)]
    [:button {:on-click #(swap! n inc)} @n]))
"
           {:file "src/demo/hsx.cljs"
            :mode :lint})
          contracts (contract/contracts-from-results [{:file "src/demo/hsx.cljs"
                                                       :findings findings}])]
      (is (= [:hsx/local-ratom-state] (mapv :rule-id findings)))
      (is (= 1 (count contracts)))
      (is (= "hsx/local-ratom-state" (:rule-id (first contracts))))
      (is (.contains (:llm-task (first contracts)) "React")))))

(deftest emits-rfx-context-contract
  (testing "RFX one-argument dispatches at integration edges need judgment"
    (let [{:keys [findings]}
          (engine/process-source
           "(ns demo.rfx
  (:require [io.factorhouse.rfx.core :as rfx]))
(defn socket-message! [event]
  (rfx/dispatch event))
"
           {:file "src/demo/rfx.cljs"
            :mode :lint})]
      (is (= [:rfx/implicit-global-dispatch] (mapv :rule-id findings)))
      (is (false? (:applied? (first findings)))))))
