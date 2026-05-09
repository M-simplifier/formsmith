(ns formsmith.rules.registry
  (:require [formsmith.rule :as rule]
            [formsmith.rules.and-seq :as and-seq]
            [formsmith.rules.blank-idioms :as blank-idioms]
            [formsmith.rules.cond-minimal :as cond-minimal]
            [formsmith.rules.cond-else :as cond-else]
            [formsmith.rules.clojuredart-flutter :as clojuredart-flutter]
            [formsmith.rules.core-idioms :as core-idioms]
            [formsmith.rules.empty-let :as empty-let]
            [formsmith.rules.framework-contracts :as framework-contracts]
            [formsmith.rules.hsx-rfx :as hsx-rfx]
            [formsmith.rules.if-do :as if-do]
            [formsmith.rules.if-nil :as if-nil]
            [formsmith.rules.keyword-get :as keyword-get]
            [formsmith.rules.negated-condition :as negated-condition]
            [formsmith.rules.namespace-hygiene :as namespace-hygiene]
            [formsmith.rules.nil-predicate :as nil-predicate]
            [formsmith.rules.nested-let :as nested-let]
            [formsmith.rules.redundant-str :as redundant-str]
            [formsmith.rules.redundant-do :as redundant-do]
            [formsmith.rules.seq-test :as seq-test]
            [formsmith.rules.seq-when :as seq-when]))

(def all-rules
  [redundant-do/rule
   redundant-str/rule
   core-idioms/rule
   and-seq/rule
   blank-idioms/rule
   empty-let/rule
   nested-let/rule
   if-do/rule
   if-nil/rule
   keyword-get/rule
   namespace-hygiene/rule
   nil-predicate/rule
   clojuredart-flutter/rule
   hsx-rfx/rule
   framework-contracts/rule
   cond-else/rule
   cond-minimal/rule
   negated-condition/rule
   seq-test/rule
   seq-when/rule])

(defn validated-rules []
  (doseq [candidate all-rules]
    (when-not (rule/valid-rule? candidate)
      (throw (ex-info "Invalid rule definition" {:rule candidate}))))
  all-rules)
