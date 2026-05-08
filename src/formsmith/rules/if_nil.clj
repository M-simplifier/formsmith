(ns formsmith.rules.if-nil
  (:require [formsmith.finding :as finding]
            [formsmith.rules.helpers :as helpers]
            [rewrite-clj.zip :as z]))

(defn- nil-literal? [form]
  (nil? form))

(defn- match-if-nil [zloc]
  (let [form (z/sexpr zloc)]
    (when (and (seq? form)
               (= 'if (first form))
               (<= 3 (count form) 4))
      (let [[_ test then else] form]
        (cond
          (or (= 3 (count form))
              (nil-literal? else))
          {:rewritten (list 'when test then)
           :rule-id :if/nil-else-branch
           :message "if with a nil else branch can be written as when"}

          (nil-literal? then)
          {:rewritten (list 'when-not test else)
           :rule-id :if/nil-then-branch
           :message "if with a nil then branch can be written as when-not"}

          :else
          nil)))))

(defn- apply-match [zloc context {:keys [rewritten rule-id message]}]
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
                :after after})}))

(def rule
  {:id :if/nil-branch
   :summary "Prefer when/when-not over if with a nil branch"
   :safety :semantic-pattern
   :kinds #{:rewrite}
   :check (fn [zloc]
            (boolean (match-if-nil zloc)))
   :apply (fn [zloc context]
            (when-let [match (match-if-nil zloc)]
              (apply-match zloc context match)))})
