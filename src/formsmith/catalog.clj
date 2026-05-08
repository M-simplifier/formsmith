(ns formsmith.catalog
  (:require [clojure.string :as str]
            [formsmith.rule :as rule]
            [formsmith.rules.registry :as registry]))

(defn rule->summary [{:keys [id summary safety tier proof kinds]}]
  {:id id
   :summary summary
   :safety safety
   :tier (or tier (rule/tier-for-safety safety))
   :proof proof
   :kinds (sort kinds)})

(defn all-rule-summaries []
  (mapv rule->summary (registry/validated-rules)))

(defn- normalize-rule-id [value]
  (cond
    (keyword? value) value
    (string? value)
    (let [candidate (if (str/includes? value "/")
                      (keyword value)
                      (keyword value))]
      candidate)
    :else nil))

(defn find-rule [rule-id]
  (let [target (normalize-rule-id rule-id)]
    (some #(when (= (:id %) target) %) (registry/validated-rules))))
