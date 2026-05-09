(ns formsmith.test-runner
  (:require [clojure.test :as t]
            [formsmith.analysis-test]
            [formsmith.rules.and-seq-test]
            [formsmith.catalog-test]
            [formsmith.rules.blank-idioms-test]
            [formsmith.rules.clojuredart-flutter-test]
            [formsmith.config-test]
            [formsmith.contract-test]
            [formsmith.corpus-test]
            [formsmith.rules.core-idioms-test]
            [formsmith.kondo-test]
            [formsmith.rules.cond-minimal-test]
            [formsmith.rules.cond-else-test]
            [formsmith.rules.empty-let-test]
            [formsmith.rules.framework-contracts-test]
            [formsmith.rules.hsx-rfx-test]
            [formsmith.rules.if-do-test]
            [formsmith.rules.if-nil-test]
            [formsmith.rules.keyword-get-test]
            [formsmith.rules.negated-condition-test]
            [formsmith.rules.namespace-hygiene-test]
            [formsmith.rules.nil-predicate-test]
            [formsmith.rules.nested-let-test]
            [formsmith.rules.redundant-do-test]
            [formsmith.rules.redundant-str-test]
            [formsmith.rules.seq-test-test]
            [formsmith.rules.seq-when-test]
            [formsmith.engine-test]
            [formsmith.framework-test]
            [formsmith.report-test]
            [formsmith.source-test]))

(defn -main []
  (let [{:keys [fail error]} (t/run-tests 'formsmith.analysis-test
                                          'formsmith.catalog-test
                                          'formsmith.rules.and-seq-test
                                          'formsmith.rules.blank-idioms-test
                                          'formsmith.rules.clojuredart-flutter-test
                                          'formsmith.config-test
                                          'formsmith.contract-test
                                          'formsmith.corpus-test
                                          'formsmith.rules.core-idioms-test
                                          'formsmith.kondo-test
                                          'formsmith.rules.cond-minimal-test
                                          'formsmith.rules.cond-else-test
                                          'formsmith.rules.empty-let-test
                                          'formsmith.rules.framework-contracts-test
                                          'formsmith.rules.hsx-rfx-test
                                          'formsmith.rules.if-do-test
                                          'formsmith.rules.if-nil-test
                                          'formsmith.rules.keyword-get-test
                                          'formsmith.rules.negated-condition-test
                                          'formsmith.rules.namespace-hygiene-test
                                          'formsmith.rules.nil-predicate-test
                                          'formsmith.rules.nested-let-test
                                          'formsmith.rules.redundant-do-test
                                          'formsmith.rules.redundant-str-test
                                          'formsmith.rules.seq-test-test
                                          'formsmith.rules.seq-when-test
                                          'formsmith.engine-test
                                          'formsmith.framework-test
                                          'formsmith.report-test
                                          'formsmith.source-test)]
    (when (pos? (+ fail error))
      (System/exit 1))))
