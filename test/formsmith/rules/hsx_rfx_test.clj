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

(deftest emits-hsx-entrypoint-contracts
  (testing "Reagent as-element entrypoints are not the canonical HSX render path"
    (let [{:keys [findings]}
          (engine/process-source
           "(ns demo.hsx
  (:require [io.factorhouse.hsx.core :as hsx]
            [reagent.core :as r]))
(defn mount! [root]
  (.render root (r/as-element [:main \"ok\"])))
"
           {:file "src/demo/hsx.cljs"
            :mode :lint})]
      (is (= [:hsx/reagent-as-element-entrypoint] (mapv :rule-id findings)))
      (is (false? (:applied? (first findings))))))
  (testing "Reagent create-class components need an HSX migration contract"
    (let [{:keys [findings]}
          (engine/process-source
           "(ns demo.hsx
  (:require [io.factorhouse.hsx.core :as hsx]
            [reagent.core :as r]))
(def panel
  (r/create-class
   {:component-did-mount (fn [_])
    :reagent-render (fn [] [:section])}))
"
           {:file "src/demo/hsx.cljs"
            :mode :lint})]
      (is (= [:hsx/reagent-class-component] (mapv :rule-id findings)))
      (is (false? (:applied? (first findings)))))))

(deftest emits-rfx-api-compatibility-contracts
  (testing "re-frame-style signals functions in reg-sub are not valid RFX shape"
    (let [{:keys [findings]}
          (engine/process-source
           "(ns demo.rfx
  (:require [io.factorhouse.rfx.core :as rfx]))
(rfx/reg-sub
 :visible-items
 (fn [_] [:items])
 (fn [items _query] items))
"
           {:file "src/demo/rfx.cljs"
            :mode :lint})]
      (is (= [:rfx/reg-sub-signals-function] (mapv :rule-id findings)))
      (is (false? (:applied? (first findings))))))
  (testing "RFX effect handlers should receive the RFX instance and the effect value"
    (let [{:keys [findings]}
          (engine/process-source
           "(ns demo.rfx
  (:require [io.factorhouse.rfx.core :as rfx]))
(rfx/reg-fx
 :toast
 (fn [message]
   (js/console.log message)))
"
           {:file "src/demo/rfx.cljs"
            :mode :lint})]
      (is (= [:rfx/reg-fx-handler-arity] (mapv :rule-id findings)))
      (is (false? (:applied? (first findings)))))))
