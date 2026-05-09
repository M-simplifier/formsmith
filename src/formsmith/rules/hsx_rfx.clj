(ns formsmith.rules.hsx-rfx
  (:require [formsmith.finding :as finding]
            [formsmith.rules.helpers :as helpers]
            [formsmith.source :as source]
            [rewrite-clj.zip :as z]))

(defn- required? [context target]
  (source/required-in-context? context target))

(defn- alias-for [context target]
  (source/alias-for-context context target))

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

(defn- hsx-reagent-as-element-match [zloc context]
  (when (and (hsx-project? context)
             (helpers/list-form? zloc))
    (let [head (helpers/head-symbol zloc)
          reagent-alias (alias-for context 'reagent.core)]
      (when (head-matches? head reagent-alias "as-element" 'reagent.core/as-element)
        {:rule-id :hsx/reagent-as-element-entrypoint
         :message "HSX entrypoints should use hsx/create-element instead of Reagent as-element"
         :suggested-source "(hsx/create-element component-form)"
         :contract {:blocked-by ["The surrounding React root/render call determines the exact replacement shape."]
                    :llm-task "Migrate this Reagent as-element entrypoint to hsx/create-element while preserving root rendering and props."
                    :acceptance ["The namespace uses io.factorhouse.hsx.core/create-element for HSX rendering."
                                 "The React root/render path still mounts the same component tree."
                                 "Frontend smoke checks pass."]}}))))

(defn- hsx-reagent-create-class-match [zloc context]
  (when (and (hsx-project? context)
             (helpers/list-form? zloc))
    (let [head (helpers/head-symbol zloc)
          reagent-alias (alias-for context 'reagent.core)]
      (when (head-matches? head reagent-alias "create-class" 'reagent.core/create-class)
        {:rule-id :hsx/reagent-class-component
         :message "Reagent class-style components should be migrated to React function components for HSX"
         :suggested-source "(defn component [...] ...)"
         :contract {:blocked-by ["Lifecycle methods and local state must be mapped to hooks or an explicit boundary."]
                    :llm-task "Migrate this Reagent create-class component to a plain React function component for HSX, using hooks or a small wrapper where needed."
                    :acceptance ["The component is a plain function component unless an error boundary requires a class wrapper."
                                 "Lifecycle/state behavior is preserved."
                                 "Frontend tests or smoke checks pass."]}}))))

(defn- rfx-project? [context]
  (required? context 'io.factorhouse.rfx.core))

(defn- rfx-reg-sub-signals-match [zloc context]
  (when (and (rfx-project? context)
             (helpers/list-form? zloc))
    (let [head (helpers/head-symbol zloc)
          rfx-alias (alias-for context 'io.factorhouse.rfx.core)
          children (helpers/child-locs zloc)
          [_ id-loc signals-loc computation-loc] children
          signals-form (some-> signals-loc z/sexpr)]
      (when (and (head-matches? head rfx-alias "reg-sub" 'io.factorhouse.rfx.core/reg-sub)
                 id-loc
                 signals-loc
                 computation-loc
                 (seq? signals-form)
                 (= 'fn (first signals-form)))
        {:rule-id :rfx/reg-sub-signals-function
         :message "RFX supports subscription vector sugar, but not re-frame signal functions"
         :suggested-source "(rfx/reg-sub :id :<- [:source] (fn [source query] ...))"
         :contract {:blocked-by ["The subscription graph must be rewritten from a signals function to supported vector dependencies."]
                    :llm-task "Rewrite this subscription to RFX-supported :<- or :-> sugar, preserving dependency order and query semantics."
                    :acceptance ["No reg-sub signals function remains for this subscription."
                                 "Subscription inputs and query results are unchanged."
                                 "Frontend tests or smoke checks pass."]}}))))

(defn- single-arg-fn? [form]
  (and (seq? form)
       (= 'fn (first form))
       (vector? (second form))
       (= 1 (count (second form)))))

(defn- rfx-reg-fx-handler-arity-match [zloc context]
  (when (and (rfx-project? context)
             (helpers/list-form? zloc))
    (let [head (helpers/head-symbol zloc)
          rfx-alias (alias-for context 'io.factorhouse.rfx.core)
          [_ id-loc handler-loc extra-loc] (helpers/child-locs zloc)
          handler-form (some-> handler-loc z/sexpr)]
      (when (and (head-matches? head rfx-alias "reg-fx" 'io.factorhouse.rfx.core/reg-fx)
                 id-loc
                 handler-loc
                 (nil? extra-loc)
                 (single-arg-fn? handler-form))
        {:rule-id :rfx/reg-fx-handler-arity
         :message "RFX reg-fx handlers should accept the RFX instance and effect value"
         :suggested-source "(rfx/reg-fx ::id (fn [rfx value] ...))"
         :contract {:blocked-by ["The handler body may need the RFX instance, snapshot, or explicit value destructuring."]
                    :llm-task "Update this RFX reg-fx handler to the two-argument handler shape while preserving side effects."
                    :acceptance ["The handler receives both the RFX instance and effect value."
                                 "Any snapshot access uses the intended RFX context."
                                 "Event/effect tests or smoke checks pass."]}}))))

(defn- match [zloc context]
  (or (reagent-atom-match zloc context)
      (rfx-implicit-dispatch-match zloc context)
      (hsx-reagent-as-element-match zloc context)
      (hsx-reagent-create-class-match zloc context)
      (rfx-reg-sub-signals-match zloc context)
      (rfx-reg-fx-handler-arity-match zloc context)))

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
