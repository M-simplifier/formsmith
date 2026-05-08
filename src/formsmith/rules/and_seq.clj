(ns formsmith.rules.and-seq
  (:require [formsmith.finding :as finding]
            [formsmith.rules.helpers :as helpers]
            [rewrite-clj.zip :as z]))

(defn- seq-target [form]
  (when (and (seq? form)
             (= 'seq (first form))
             (= 2 (count form)))
    (second form)))

(defn- not-empty-form [target-form]
  (list 'not-empty target-form))

(defn- not-empty-target [form]
  (when (and (seq? form)
             (= 'not-empty (first form))
             (= 2 (count form)))
    (second form)))

(defn- raw-plus-not-empty-pair? [left right]
  (or (= left (not-empty-target right))
      (= right (not-empty-target left))))

(defn- normalize-and-clauses [clauses]
  (let [rewritten (mapv (fn [clause]
                          (if-let [target-form (seq-target clause)]
                            (if (helpers/seq-bindable-form? target-form)
                              (not-empty-form target-form)
                              clause)
                            clause))
                        clauses)]
    (reduce (fn [acc clause]
              (if-let [previous (peek acc)]
                (if (raw-plus-not-empty-pair? previous clause)
                  (conj (pop acc)
                        (if (not-empty-target previous)
                          previous
                          clause))
                  (conj acc clause))
                [clause]))
            []
            rewritten)))

(defn- and-seq-match [zloc]
  (when (and (helpers/list-form? zloc)
             (not (helpers/source-sensitive? zloc))
             (not (some-> zloc z/up helpers/source-sensitive?))
             (helpers/boolean-test-position? zloc))
    (let [form (z/sexpr zloc)]
      (when (and (seq? form)
                 (= 'and (first form)))
        (let [clauses (vec (rest form))
              rewritten-clauses (normalize-and-clauses clauses)]
          (when (not= clauses rewritten-clauses)
            {:rewritten (if (= 1 (count rewritten-clauses))
                          (first rewritten-clauses)
                          (list* 'and rewritten-clauses))
             :rule-id :condition/and-seq-not-empty
             :message "boolean test that uses seq inside and can be written with not-empty"}))))))

(def rule
  {:id :condition/and-seq
   :summary "Prefer not-empty over and plus seq in boolean test positions"
   :safety :semantic-pattern
   :kinds #{:rewrite}
   :check (fn [zloc]
            (boolean (and-seq-match zloc)))
   :apply (fn [zloc context]
            (when-let [{:keys [rewritten rule-id message]} (and-seq-match zloc)]
              (let [before (helpers/node-string zloc)
                    applied? (helpers/autofix-allowed? context zloc :semantic-pattern)
                    updated (if applied?
                              (helpers/replace-with-form zloc rewritten)
                              zloc)
                    after (helpers/node-string updated)]
                {:zloc updated
                 :finding (finding/make
                           {:rule-id rule-id
                            :message message
                            :safety :semantic-pattern
                            :severity :warning
                            :source :formsmith
                            :file (:file context)
                            :line (helpers/line zloc)
                            :column (helpers/column zloc)
                            :applied? applied?
                            :kind :rewrite
                            :before before
                            :after after})})))})
