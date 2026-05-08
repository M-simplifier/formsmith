(ns formsmith.source-test
  (:require [clojure.test :refer [deftest is testing]]
            [formsmith.source :as source]))

(deftest scans-clojuredart-string-and-symbol-requires
  (testing "source scan covers .cljd-style string package requires"
    (let [deps (source/namespace-deps-from-source
                "src/demo/mobile.cljd"
                "(ns demo.mobile
  (:require [\"package:flutter/material.dart\" :as m]
            [cljd.flutter :as f]))")
          by-target (into {} (map (juxt :to identity) deps))]
      (is (= 'm (:alias (get by-target "package:flutter/material.dart"))))
      (is (= 'f (:alias (get by-target 'cljd.flutter)))))))

(deftest scans-prefix-requires-without-key-value-options
  (testing "prefix require vectors do not break source scanning"
    (let [deps (source/namespace-deps-from-source
                "src/demo/core.clj"
                "(ns demo.core
  (:require [clojure [string :as str] [set :as set]]
            [malli.core :as m]))")
          by-target (into {} (map (juxt :to identity) deps))]
      (is (= 'm (:alias (get by-target 'malli.core))))
      (is (contains? by-target 'clojure)))))
