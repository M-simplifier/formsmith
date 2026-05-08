(ns formsmith.rules.clojuredart-flutter-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
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
