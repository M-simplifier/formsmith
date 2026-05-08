(ns formsmith.rules.hsx-rfx
  (:require [formsmith.finding :as finding]
            [formsmith.rules.helpers :as helpers]
            [formsmith.source :as source]))

(defn- required? [context target]
  (source/required? (:source context) target))

(defn- alias-for [context target]
  (source/alias-for (:source context) target))

(defn- head-matches? [head alias target-name full-symbol]
  (or (= full-symbol head)
      (and alias (= (symbol (str alias) target-name) head))))

(defn- hsx-project? [context]
  (required? context 'io.factorhouse.hsx.core))

(defn- reagent-atom-match [zloc context]
  (when (and (hsx-project? context)
             (helpers/list-form? zloc))
    (let [head (helpers/head-symbol zloc)
          reagent-alias (alias-for context 'reagent.core)]
      (when (head-matches? head reagent-alias "atom" 'reagent.core/atom)
        {:rule-id :hsx/local-ratom-state
         :message "HSX components should use React local state hooks instead of Reagent ratoms"
         :suggested-source "(let [[state set-state] (react/useState initial-value)] ...)"
         :contract {:blocked-by ["A local ratom-to-useState rewrite changes surrounding bindings and event handlers, not just this call site."]
                    :llm-task "Migrate this HSX component's local state from a Reagent atom to React useState/useReducer while preserving event behavior and render identity."
                    :acceptance ["The component remains a plain function component."
                                 "State updates use React hooks and do not create Reagent ratoms."
                                 "Affected UI tests or smoke checks pass."]}}))))

(defn- rfx-implicit-dispatch-match [zloc context]
  (when (helpers/list-form? zloc)
    (let [head (helpers/head-symbol zloc)
          rfx-alias (alias-for context 'io.factorhouse.rfx.core)
          [_ event-loc extra-loc] (helpers/child-locs zloc)]
      (when (and (head-matches? head rfx-alias "dispatch" 'io.factorhouse.rfx.core/dispatch)
                 event-loc
                 (nil? extra-loc))
        {:rule-id :rfx/implicit-global-dispatch
         :message "RFX edge dispatches should make the RFX context boundary explicit"
         :suggested-source "(rfx/dispatch rfx-context event)"
         :contract {:blocked-by ["The local static pass cannot tell whether this dispatch runs inside a React component, an event edge, or test setup."]
                    :llm-task "Decide whether this dispatch belongs inside a component hook path or an external integration edge. Use RFX hooks in components and explicit RFX context at external edges."
                    :acceptance ["Component code uses RFX hooks rather than global dispatch calls."
                                 "External systems pass the intended RFX context explicitly."
                                 "Existing event ordering and tests are preserved."]}}))))

(defn- match [zloc context]
  (or (reagent-atom-match zloc context)
      (rfx-implicit-dispatch-match zloc context)))

(def rule
  {:id :hsx-rfx/contracts
   :summary "Emit HSX/RFX migration contracts for state and context shapes that need judgment"
   :safety :unsafe
   :tier :llm-refactor
   :kinds #{:rewrite}
   :check (fn [zloc]
            (helpers/list-form? zloc))
   :apply (fn [zloc context]
            (if-let [{:keys [rule-id message suggested-source contract]} (match zloc context)]
              {:zloc zloc
               :finding (finding/make
                         {:rule-id rule-id
                          :message message
                          :safety :unsafe
                          :severity :warning
                          :source :formsmith
                          :file (:file context)
                          :line (helpers/line zloc)
                          :column (helpers/column zloc)
                          :applied? false
                          :kind :rewrite
                          :suggested-source suggested-source
                          :contract contract
                          :before (helpers/node-string zloc)
                          :after (helpers/node-string zloc)})}
              {:zloc zloc
               :finding nil}))})
