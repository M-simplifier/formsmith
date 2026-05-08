(ns formsmith.report-test
  (:require [clojure.data.json :as json]
            [clojure.test :refer [deftest is testing]]
            [formsmith.report :as report]))

(deftest json-report-omits-source-heavy-fields-by-default
  (testing "CI JSON artifacts do not leak full proprietary source unless asked"
    (let [result {:file "src/app/core.clj"
                  :source "(if (not ok?) :a :b)\n"
                  :changed? true
                  :findings [{:rule-id :if/not-condition
                              :line 1
                              :column 1
                              :before "(if (not ok?) :a :b)"
                              :after "(if-not ok? :a :b)"
                              :suggested-source "(if-not ok? :a :b)"}]}
          summary {:files 1 :changed 1 :findings 1}
          data (json/read-str (report/json-report [result] summary)
                              :key-fn keyword)
          finding (get-in data [:results 0 :findings 0])]
      (is (not (contains? (get-in data [:results 0]) :source)))
      (is (not (contains? finding :before)))
      (is (not (contains? finding :after)))
      (is (not (contains? finding :suggested-source))))))

(deftest json-report-can-include-source-when-explicit
  (testing "debug mode can still expose full rewrite context"
    (let [result {:file "src/app/core.clj"
                  :source "(if (not ok?) :a :b)\n"
                  :changed? true
                  :findings [{:rule-id :if/not-condition
                              :before "(if (not ok?) :a :b)"
                              :after "(if-not ok? :a :b)"}]}
          data (json/read-str (report/json-report [result]
                                                  {:files 1 :changed 1 :findings 1}
                                                  {:include-source? true})
                              :key-fn keyword)]
      (is (= "(if (not ok?) :a :b)\n"
             (get-in data [:results 0 :source])))
      (is (= "(if (not ok?) :a :b)"
             (get-in data [:results 0 :findings 0 :before]))))))
