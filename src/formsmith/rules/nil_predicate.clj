(ns formsmith.rules.nil-predicate
  (:require [formsmith.finding :as finding]
            [formsmith.rules.helpers :as helpers]
            [rewrite-clj.zip :as z]))

(defn- nil-literal? [loc]
  (nil? (some-> loc z/sexpr)))

(defn- comparison-match [zloc]
  (when (and (helpers/list-form? zloc)
             (not (helpers/inside-thread-macro-step? zloc)))
    (let [head (helpers/head-symbol zloc)
          [_ left-loc right-loc extra-loc] (helpers/child-locs zloc)]
      (when (and left-loc
                 right-loc
                 (nil? extra-loc))
        (let [left-nil? (nil-literal? left-loc)
              right-nil? (nil-literal? right-loc)
              target-loc (cond
                           left-nil? right-loc
                           right-nil? left-loc)]
          (when (and target-loc
                     (not (and left-nil? right-nil?)))
            (case head
              = {:rule-id :nil/eq
                 :message "= nil can be written as nil?"
                 :replacement-head "nil?"
                 :target-loc target-loc}
              clojure.core/= {:rule-id :nil/eq
                              :message "= nil can be written as nil?"
                              :replacement-head "clojure.core/nil?"
                              :target-loc target-loc}
              not= {:rule-id :nil/not-eq
                    :message "not= nil can be written as some?"
                    :replacement-head "some?"
                    :target-loc target-loc}
              clojure.core/not= {:rule-id :nil/not-eq
                                 :message "not= nil can be written as some?"
                                 :replacement-head "clojure.core/some?"
                                 :target-loc target-loc}
              nil)))))))

(defn- rewritten-source [{:keys [replacement-head target-loc]}]
  (str "(" replacement-head " " (helpers/node-string target-loc) ")"))

(def rule
  {:id :nil/predicate
   :summary "Prefer nil? and some? over equality checks against nil"
   :safety :syntax-safe
   :kinds #{:rewrite}
   :check (fn [zloc]
            (boolean (comparison-match zloc)))
   :apply (fn [zloc context]
            (when-let [{:keys [rule-id message] :as match} (comparison-match zloc)]
              (let [before (helpers/node-string zloc)
                    applied? (and (helpers/autofix-allowed? context zloc :syntax-safe)
                                  (not (some-> zloc z/up helpers/source-sensitive?)))
                    updated (if applied?
                              (helpers/replace-with-string zloc (rewritten-source match))
                              zloc)
                    after (helpers/node-string updated)]
                {:zloc updated
                 :finding (finding/make
                           {:rule-id rule-id
                            :message message
                            :safety :syntax-safe
                            :severity :warning
                            :source :formsmith
                            :file (:file context)
                            :line (helpers/line zloc)
                            :column (helpers/column zloc)
                            :applied? applied?
                            :kind :rewrite
                            :before before
                            :after after})})))})
