(ns cold-start-demo.report-test
  (:require [clojure.test :refer [deftest is]]
            [cold-start-demo.report :as report]))

(deftest status-label-shows-ready-state
  (is (= "ready" (report/status-label true)))
  (is (= "pending" (report/status-label false))))
