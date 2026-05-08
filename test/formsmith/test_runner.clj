(ns formsmith.test-runner
  (:require [clojure.test :as t]
            [formsmith.analysis-test]
            [formsmith.rules.and-seq-test]
            [formsmith.catalog-test]
            [formsmith.rules.blank-idioms-test]
            [formsmith.contract-test]
            [formsmith.corpus-test]
            [formsmith.kondo-test]
            [formsmith.rules.cond-minimal-test]
            [formsmith.rules.cond-else-test]
            [formsmith.rules.empty-let-test]
            [formsmith.rules.if-do-test]
            [formsmith.rules.if-nil-test]
            [formsmith.rules.negated-condition-test]
            [formsmith.rules.nested-let-test]
            [formsmith.rules.redundant-do-test]
            [formsmith.rules.redundant-str-test]
            [formsmith.rules.seq-test-test]
            [formsmith.rules.seq-when-test]
            [formsmith.engine-test]
            [formsmith.framework-test]))

(defn -main []
  (let [{:keys [fail error]} (t/run-tests 'formsmith.analysis-test
                                          'formsmith.catalog-test
                                          'formsmith.rules.and-seq-test
                                          'formsmith.rules.blank-idioms-test
                                          'formsmith.contract-test
                                          'formsmith.corpus-test
                                          'formsmith.kondo-test
                                          'formsmith.rules.cond-minimal-test
                                          'formsmith.rules.cond-else-test
                                          'formsmith.rules.empty-let-test
                                          'formsmith.rules.if-do-test
                                          'formsmith.rules.if-nil-test
                                          'formsmith.rules.negated-condition-test
                                          'formsmith.rules.nested-let-test
                                          'formsmith.rules.redundant-do-test
                                          'formsmith.rules.redundant-str-test
                                          'formsmith.rules.seq-test-test
                                          'formsmith.rules.seq-when-test
                                          'formsmith.engine-test
                                          'formsmith.framework-test)]
    (when (pos? (+ fail error))
      (System/exit 1))))
