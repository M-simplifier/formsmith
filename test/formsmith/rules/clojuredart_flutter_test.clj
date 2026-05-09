(ns formsmith.rules.clojuredart-flutter-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [formsmith.contract :as contract]
            [formsmith.engine :as engine]))

(def child-chain-source
  "(ns demo.mobile
  (:require [\"package:flutter/material.dart\" :as m]
            [cljd.flutter :as f]))

(defn fab [open toggle]
  (m/IgnorePointer
    .ignoring (boolean @open)
    .child
    (m/AnimatedContainer
      .duration (m/Duration .milliseconds 250)
      .child
      (m/FloatingActionButton
        .onPressed toggle
        .child
        (m/Icon m.Icons/create)))))
")

(deftest rewrites-long-flutter-child-chain-to-nest
  (testing "ClojureDart widget chains get a visible canonical f/nest shape"
    (let [{:keys [source findings]}
          (engine/process-source child-chain-source
                                 {:file "src/demo/mobile.cljd"
                                  :mode :fix
                                  :format? false})
          finding (first findings)]
      (is (= :clojuredart/widget-child-chain (:rule-id finding)))
      (is (:applied? finding))
      (is (.contains source "(f/nest"))
      (is (.contains source "(m/IgnorePointer .ignoring (boolean @open))"))
      (is (.contains source "(m/FloatingActionButton .onPressed toggle)"))
      (is (not (.contains source ".child\n    (m/AnimatedContainer"))))))

(deftest skips-nest-rewrite-without-current-cljd-flutter-alias
  (testing "the rewrite requires an explicit current cljd.flutter alias"
    (let [{:keys [source findings]}
          (engine/process-source (str/replace child-chain-source
                                              "[cljd.flutter :as f]"
                                              "[cljd.flutter.alpha :as f]")
                                 {:file "src/demo/mobile.cljd"
                                  :mode :fix
                                  :format? false})]
      (is (= child-chain-source
             (str/replace source
                          "[cljd.flutter.alpha :as f]"
                          "[cljd.flutter :as f]")))
      (is (= [:clojuredart/deprecated-flutter-alpha]
             (mapv :rule-id findings))))))

(deftest tolerates-clojuredart-type-metadata
  (testing "ClojureDart type annotations do not make unrelated sexpr rules crash"
    (let [{:keys [source findings]}
          (engine/process-source
           "(ns demo.mobile
  (:require [\"package:flutter/material.dart\" :as m]
            [cljd.flutter :as f]))

(defn animated
  [& {:keys [^#/(m/Animation double) progress child]}]
  (f/widget
    :watch [v progress]
    child))
"
           {:file "src/demo/mobile.cljd"
            :mode :fix
            :format? false})]
      (is (.contains source "^#/(m/Animation double) progress"))
      (is (empty? findings)))))

(deftest emits-widget-helper-contracts
  (testing "local atom state inside f/widget is a contract"
    (let [source "(ns demo.mobile
  (:require [\"package:flutter/material.dart\" :as m]
            [cljd.flutter :as f]))

(f/widget
  (let [open (atom false)]
    (m/Text \"x\")))"
          {:keys [findings]} (engine/process-source source
                                                    {:file "demo.cljd"
                                                     :mode :lint})]
      (is (= [:clojuredart/widget-local-atom-state]
             (mapv :rule-id findings)))
      (is (= "clojuredart/widget-local-atom-state"
             (:rule-id (first (contract/contracts-from-results [{:file "demo.cljd"
                                                                 :findings findings}])))))))
  (testing "large f/widget body is a component extraction contract"
    (let [source "(ns demo.mobile
  (:require [cljd.flutter :as f]))

(f/widget
  a b c d e f g h i)"
          {:keys [findings]} (engine/process-source source
                                                    {:file "demo.cljd"
                                                     :mode :lint})]
      (is (= [:clojuredart/widget-body-extraction]
             (mapv :rule-id findings))))))

(deftest emits-flutter-lifecycle-contracts
  (testing "controller constructors are reviewed for :with"
    (let [{:keys [findings]}
          (engine/process-source "(ns demo.mobile
  (:require [\"package:flutter/material.dart\" :as m]
            [cljd.flutter :as f]))

(m/TextEditingController.)"
                                 {:file "demo.cljd" :mode :lint})]
      (is (= [:clojuredart/controller-without-widget-with]
             (mapv :rule-id findings)))))
  (testing "animation controllers are reviewed for ticker ownership"
    (let [{:keys [findings]}
          (engine/process-source "(ns demo.mobile
  (:require [\"package:flutter/material.dart\" :as m]
            [cljd.flutter :as f]))

(m/AnimationController. .duration duration)"
                                 {:file "demo.cljd" :mode :lint})]
      (is (= [:clojuredart/animation-controller-ticker]
             (mapv :rule-id findings)))))
  (testing "Widget.of context lookups are reviewed for f/widget options"
    (let [{:keys [findings]}
          (engine/process-source "(ns demo.mobile
  (:require [\"package:flutter/material.dart\" :as m]
            [cljd.flutter :as f]))

(m/Theme.of context)"
                                 {:file "demo.cljd" :mode :lint})]
      (is (= [:clojuredart/widget-of-context-helper]
             (mapv :rule-id findings)))))
  (testing "Builder forms are reviewed for f/widget"
    (let [{:keys [findings]}
          (engine/process-source "(ns demo.mobile
  (:require [\"package:flutter/material.dart\" :as m]
            [cljd.flutter :as f]))

(m/Builder .builder (fn [context] (m/Text \"x\")))"
                                 {:file "demo.cljd" :mode :lint})]
      (is (= [:clojuredart/builder-to-widget]
             (mapv :rule-id findings))))))

(deftest accepts-canonical-widget-option-contexts
  (testing "controller constructors inside f/widget :with are not re-reported"
    (let [{:keys [findings]}
          (engine/process-source "(ns demo.mobile
  (:require [\"package:flutter/material.dart\" :as m]
            [cljd.flutter :as f]))

(f/widget
  :with [controller (m/TextEditingController.)]
  (m/TextField .controller controller))"
                                 {:file "demo.cljd" :mode :lint})]
      (is (empty? findings))))
  (testing "Widget.of calls inside :inherit are accepted"
    (let [{:keys [findings]}
          (engine/process-source "(ns demo.mobile
  (:require [\"package:flutter/material.dart\" :as m]
            [cljd.flutter :as f]))

(f/widget
  :context context
  :inherit [theme (m/Theme.of context)]
  (m/Text \"x\"))"
                                 {:file "demo.cljd" :mode :lint})]
      (is (empty? findings)))))
