(ns formsmith.framework-test
  (:require [clojure.test :refer [deftest is testing]]
            [formsmith.framework :as framework]))

(deftest detects-framework-profiles-from-namespace-deps
  (testing "known framework namespaces produce initial canonical profiles"
    (let [profiles (framework/detect [{:from 'demo.ui
                                        :to 're-frame.core
                                        :alias 'rf
                                        :file "src/demo/ui.cljs"
                                        :line 1
                                        :column 24}
                                       {:from 'demo.ui
                                        :to 'reagent.core
                                        :alias 'r
                                        :file "src/demo/ui.cljs"
                                        :line 1
                                        :column 47}
                                       {:from 'demo.server
                                        :to 'reitit.ring
                                        :alias 'ring
                                        :file "src/demo/server.clj"
                                        :line 2
                                        :column 12}
                                       {:from 'demo.system
                                        :to 'integrant.core
                                        :alias 'ig
                                        :file "src/demo/system.clj"
                                        :line 2
                                        :column 12}])
          by-id (into {} (map (juxt :id identity) profiles))]
      (is (contains? by-id "re-frame"))
      (is (contains? by-id "reagent"))
      (is (contains? by-id "reitit"))
      (is (contains? by-id "integrant"))
      (is (= "cljs-state" (get-in by-id ["re-frame" :category])))
      (is (= "re-frame.core" (get-in by-id ["re-frame" :evidence 0 :to])))
      (is (seq (get-in by-id ["integrant" :canonical-guidance]))))))

(deftest ignores-ordinary-namespaces
  (testing "ordinary deps do not create framework profiles"
    (is (empty? (framework/detect [{:from 'demo.core
                                    :to 'clojure.string
                                    :alias 'str}])))))
