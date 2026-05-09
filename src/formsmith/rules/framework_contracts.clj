(ns formsmith.rules.framework-contracts
  (:require [clojure.string :as str]
            [formsmith.finding :as finding]
            [formsmith.rules.helpers :as helpers]
            [formsmith.source :as source]
            [rewrite-clj.zip :as z]))

(defn- deps [context]
  (source/cached-namespace-deps context))

(defn- required? [context target]
  (source/required-in-context? context target))

(defn- required-prefix? [context prefix]
  (boolean
   (some (fn [{:keys [to]}]
           (str/starts-with? (str to) prefix))
         (deps context))))

(defn- alias-for [context target]
  (source/alias-for-context context target))

(defn- alias-for-any [context targets]
  (some #(alias-for context %) targets))

(defn- head-matches? [head alias target-name full-symbol]
  (or (= full-symbol head)
      (and alias (= (symbol (str alias) target-name) head))))

(defn- list-sexpr? [form]
  (seq? form))

(defn- call-head [form]
  (when (list-sexpr? form)
    (first form)))

(defn- contains-call-head? [form heads]
  (boolean
   (some (fn [node]
           (contains? heads (call-head node)))
         (tree-seq coll? seq form))))

(defn- ns-form? [zloc]
  (and (helpers/list-form? zloc)
       (= 'ns (helpers/head-symbol zloc))))

(defn- vector-form? [zloc]
  (= :vector (z/tag zloc)))

(defn- map-form? [zloc]
  (= :map (z/tag zloc)))

(defn- ns-source-includes? [context text]
  (str/includes? (:source context) text))

(defn- framework-category-count [context]
  (let [groups [(required? context 're-frame.core)
                (required? context 'reagent.core)
                (required? context 'io.factorhouse.hsx.core)
                (required? context 'io.factorhouse.rfx.core)
                (required? context 'integrant.core)
                (required-prefix? context "ring.")
                (required-prefix? context "reitit.")
                (required-prefix? context "malli.")]]
    (count (filter true? groups))))

(defn- namespace-responsibility-match [zloc context]
  (when (and (ns-form? zloc)
             (>= (framework-category-count context) 3))
    {:rule-id :namespace/mixed-framework-responsibility
     :message "Namespaces that mix several framework surfaces should be reviewed for responsibility boundaries"
     :suggested-source "Split UI/state/http/schema/system responsibilities into named namespaces"
     :contract {:blocked-by ["The correct split depends on the application's domain, routing, and lifecycle ownership."]
                :llm-task "Review this namespace for mixed framework responsibilities and split or document the boundary when it combines UI, state, HTTP, schema, or system lifecycle work."
                :acceptance ["Each resulting namespace has one dominant responsibility."
                             "Cross-namespace dependencies remain acyclic or explicitly justified."
                             "Project tests and Formsmith check pass."]}}))

(defn- hiccup-vector? [form]
  (and (vector? form)
       (keyword? (first form))))

(defn- hiccup-attribute-shape-match [zloc context]
  (when (and (or (required? context 'reagent.core)
                 (required? context 're-frame.core)
                 (required? context 'io.factorhouse.hsx.core))
             (vector-form? zloc))
    (let [form (z/sexpr zloc)
          attrs (second form)]
      (when (and (hiccup-vector? form)
                 (map? attrs)
                 (contains? attrs :class)
                 (string? (:class attrs))
                 (str/includes? (:class attrs) " "))
        {:rule-id :hiccup/attribute-shape
         :message "Hiccup class attributes with space-delimited strings should be reviewed for a project-local class composition helper"
         :suggested-source "[:div {:class (classes ...)} ...]"
         :contract {:blocked-by ["Class composition style depends on the project's CSS and helper conventions."]
                    :llm-task "Replace ad hoc Hiccup class string assembly with the project's canonical class composition helper or document why the literal string is intentional."
                    :acceptance ["The rendered classes are unchanged."
                                 "The class construction follows a single project-local convention."
                                 "Frontend smoke tests pass."]}}))))

(defn- reagent-project? [context]
  (and (required? context 'reagent.core)
       (not (required? context 'io.factorhouse.hsx.core))))

(defn- reagent-atom-match [zloc context]
  (when (and (reagent-project? context)
             (helpers/list-form? zloc))
    (let [head (helpers/head-symbol zloc)
          reagent-alias (alias-for context 'reagent.core)]
      (when (head-matches? head reagent-alias "atom" 'reagent.core/atom)
        {:rule-id :reagent/local-ratom-state
         :message "Reagent local ratom state should be reviewed for ownership and component boundary clarity"
         :suggested-source "(let [state (r/atom initial)] ...)"
         :contract {:blocked-by ["Local ratoms are valid in Reagent, but ownership, reset behavior, and form-2 lifetimes require surrounding component context."]
                    :llm-task "Review this Reagent local ratom and decide whether it belongs in component-local state, app-db, or a smaller extracted component."
                    :acceptance ["State ownership is visible at the component boundary."
                                 "No state reset or lifetime behavior changes unintentionally."
                                 "Frontend tests or smoke checks pass."]}}))))

(defn- reagent-form2-match [zloc context]
  (when (and (reagent-project? context)
             (helpers/list-form? zloc)
             (= 'defn (helpers/head-symbol zloc)))
    (let [[_ name-loc args-loc body-loc] (helpers/child-locs zloc)
          body-form (some-> body-loc z/sexpr)]
      (when (and name-loc args-loc
                 (list-sexpr? body-form)
                 (= 'fn (first body-form)))
        {:rule-id :reagent/form-2-component
         :message "Reagent form-2 components should be reviewed for state lifetime and naming clarity"
         :suggested-source "(defn component [args] (let [...] (fn [...] ...)))"
         :contract {:blocked-by ["Form-2 components are valid when state lifetime is intentional; the tool cannot prove that from one form."]
                    :llm-task "Review this Reagent form-2 component. Keep it only when render closure lifetime is intentional; otherwise migrate to a simpler function component or extract state ownership."
                    :acceptance ["The component form is intentionally chosen and easy to name."
                                 "State lifetime across re-renders remains correct."
                                 "Frontend tests or smoke checks pass."]}}))))

(defn- re-frame-alias [context]
  (alias-for context 're-frame.core))

(defn- re-frame-head? [context head target-name full-symbol]
  (head-matches? head (re-frame-alias context) target-name full-symbol))

(defn- re-frame-reg-event-db-match [zloc context]
  (when (and (required? context 're-frame.core)
             (helpers/list-form? zloc))
    (let [head (helpers/head-symbol zloc)
          form (z/sexpr zloc)
          alias (re-frame-alias context)
          dispatch-heads (cond-> #{'dispatch 're-frame.core/dispatch
                                   'js/fetch 'ajax.core/GET 'ajax.core/POST}
                           alias (conj (symbol (str alias) "dispatch")))]
      (when (and (re-frame-head? context head "reg-event-db" 're-frame.core/reg-event-db)
                 (contains-call-head? form dispatch-heads))
        {:rule-id :re-frame/reg-event-db-side-effect
         :message "reg-event-db handlers should stay pure; side effects belong in reg-event-fx or effects"
         :suggested-source "(rf/reg-event-fx ::event (fn [{:keys [db]} event] {:db db :fx [...] }))"
         :contract {:blocked-by ["Moving side effects changes event/effect ordering and requires app-specific effect registration."]
                    :llm-task "Move side effects out of this reg-event-db handler into reg-event-fx or a registered effect while preserving db updates and event ordering."
                    :acceptance ["The db update remains pure."
                                 "Side effects are represented as effects."
                                 "Event/effect tests or frontend smoke checks pass."]}}))))

(defn- re-frame-reg-sub-match [zloc context]
  (when (and (required? context 're-frame.core)
             (helpers/list-form? zloc))
    (let [head (helpers/head-symbol zloc)
          [_ id-loc signals-loc computation-loc] (helpers/child-locs zloc)
          signals-form (some-> signals-loc z/sexpr)]
      (when (and (re-frame-head? context head "reg-sub" 're-frame.core/reg-sub)
                 id-loc
                 computation-loc
                 (list-sexpr? signals-form)
                 (= 'fn (first signals-form)))
        {:rule-id :re-frame/reg-sub-signals-shape
         :message "reg-sub signal functions should be reviewed for subscription graph readability"
         :suggested-source "(rf/reg-sub ::id :<- [::source] (fn [source query] ...))"
         :contract {:blocked-by ["The dependency graph and query shape determine whether vector sugar is clearer."]
                    :llm-task "Review this re-frame subscription and prefer explicit :<- dependencies or a named helper when it improves graph readability."
                    :acceptance ["Subscription dependencies are easy to scan."
                                 "Query semantics are unchanged."
                                 "Subscription tests or frontend smoke checks pass."]}}))))

(defn- re-frame-namespace-placement-match [zloc context]
  (when (and (ns-form? zloc)
             (required? context 're-frame.core)
             (or (and (ns-source-includes? context "reg-event")
                      (ns-source-includes? context "reg-sub"))
                 (and (ns-source-includes? context "reg-fx")
                      (ns-source-includes? context "reg-event"))))
    {:rule-id :re-frame/mixed-registration-namespace
     :message "Namespaces that mix re-frame events, effects, and subscriptions should be reviewed for placement"
     :suggested-source "app.events / app.effects / app.subs"
     :contract {:blocked-by ["The correct namespace split depends on app size and team conventions."]
                :llm-task "Review whether this namespace should be split into event, effect, coeffect, and subscription namespaces, or documented as an intentionally small module."
                :acceptance ["Registration placement follows a consistent project convention."
                             "No registration keyword or require path is broken."
                             "Frontend tests or smoke checks pass."]}}))

(defn- react-alias [context]
  (alias-for-any context ["react" 'react]))

(defn- react-hook-match [zloc context]
  (when (and (required? context 'io.factorhouse.hsx.core)
             (helpers/list-form? zloc))
    (let [head (helpers/head-symbol zloc)
          alias (react-alias context)]
      (when (and (head-matches? head alias "useEffect" 'react/useEffect)
                 (= 2 (count (helpers/child-locs zloc))))
        {:rule-id :hsx/react-hook-deps
         :message "HSX React effect hooks should make dependency decisions explicit"
         :suggested-source "(react/useEffect effect-fn deps)"
         :contract {:blocked-by ["The correct dependency vector depends on captured values and intentional effect lifetime."]
                    :llm-task "Review this HSX useEffect call and add an explicit dependency vector or document why the effect intentionally runs after every render."
                    :acceptance ["Effect dependencies are explicit."
                                 "No stale closure or infinite render behavior is introduced."
                                 "Frontend smoke checks pass."]}}))))

(defn- hsx-inline-component-match [zloc context]
  (when (and (required? context 'io.factorhouse.hsx.core)
             (helpers/list-form? zloc))
    (let [head (helpers/head-symbol zloc)
          hsx-alias (alias-for context 'io.factorhouse.hsx.core)
          [_ component-loc] (helpers/child-locs zloc)
          component-form (some-> component-loc z/sexpr)]
      (when (and (head-matches? head hsx-alias "create-element" 'io.factorhouse.hsx.core/create-element)
                 (list-sexpr? component-form)
                 (= 'fn (first component-form)))
        {:rule-id :hsx/inline-component-identity
         :message "Inline anonymous HSX components should be reviewed for stable identity and memoization boundaries"
         :suggested-source "(defn NamedComponent [props] ...)"
         :contract {:blocked-by ["Component identity, metadata, and memoization policy are application-specific."]
                    :llm-task "Extract this inline HSX component into a named function component when stable identity, metadata, or memoization would improve reviewability."
                    :acceptance ["Component identity is stable where React expects it."
                                 "Memoization or metadata choices are explicit."
                                 "Frontend smoke checks pass."]}}))))

(defn- ring-response-map-match [zloc context]
  (when (and (required-prefix? context "ring.")
             (map-form? zloc))
    (let [form (z/sexpr zloc)]
      (when (and (contains? form :status)
                 (contains? form :body))
        {:rule-id :ring/raw-response-map
         :message "Raw Ring response maps should be reviewed for response helper consistency"
         :suggested-source "(response/status (response/response body) status)"
         :contract {:blocked-by ["Headers, streaming bodies, and project response helpers determine the exact rewrite."]
                    :llm-task "Replace or justify this raw Ring response map using the project's canonical response helpers while preserving status, headers, and body semantics."
                    :acceptance ["HTTP status, headers, and body are unchanged."
                                 "Response construction follows one project convention."
                                 "Handler tests pass."]}}))))

(defn- ring-middleware-match [zloc context]
  (when (and (required-prefix? context "ring.")
             (helpers/list-form? zloc)
             (= 'defn (helpers/head-symbol zloc)))
    (let [[_ name-loc] (helpers/child-locs zloc)
          name-form (some-> name-loc z/sexpr)
          form (z/sexpr zloc)]
      (when (and (symbol? name-form)
                 (str/starts-with? (name name-form) "wrap-")
                 (contains-call-head? form #{'fn}))
        {:rule-id :ring/middleware-shape
         :message "Ring middleware should make handler wrapping, arity, and order easy to review"
         :suggested-source "(defn wrap-name [handler opts] ...)"
         :contract {:blocked-by ["Middleware arity, async support, and ordering are behavior-sensitive."]
                    :llm-task "Review this middleware for clear handler wrapping, supported arities, and order-sensitive behavior. Extract helpers or document the order if needed."
                    :acceptance ["Supported request/response arities are explicit."
                                 "Middleware order is visible at composition sites."
                                 "Handler or integration tests pass."]}}))))

(defn- route-vector? [form]
  (and (vector? form)
       (string? (first form))))

(defn- reitit-route-inline-handler-match [zloc context]
  (when (and (required-prefix? context "reitit.")
             (vector-form? zloc))
    (let [form (z/sexpr zloc)]
      (when (and (route-vector? form)
                 (contains-call-head? form #{'fn}))
        {:rule-id :reitit/inline-route-handler
         :message "Inline Reitit route handlers should be reviewed for named handler boundaries"
         :suggested-source "[\"/path\" {:get handler-fn}]"
         :contract {:blocked-by ["Handler naming and extraction depend on route table size and domain boundaries."]
                    :llm-task "Extract inline Reitit route handlers into named functions when it improves route-table readability and handler testability."
                    :acceptance ["Route data remains data-oriented and easy to scan."
                                 "Handler behavior and route names are unchanged."
                                 "Route/handler tests pass."]}}))))

(defn- reitit-middleware-coercion-match [zloc context]
  (when (and (required-prefix? context "reitit.")
             (map-form? zloc))
    (let [form (z/sexpr zloc)]
      (when (and (contains? form :middleware)
                 (contains? form :coercion))
        {:rule-id :reitit/middleware-coercion-placement
         :message "Reitit middleware and coercion placement should be reviewed together"
         :suggested-source "{:coercion ... :middleware [...]}"
         :contract {:blocked-by ["Middleware order and coercion defaults are framework- and app-specific."]
                    :llm-task "Review this Reitit route data so coercion, muuntaja, parameters, responses, and middleware order are placed at the narrowest clear boundary."
                    :acceptance ["Middleware order is intentional."
                                 "Coercion configuration is not duplicated unnecessarily."
                                 "Route tests pass."]}}))))

(defn- reitit-frontend-rfx-match [zloc context]
  (when (and (ns-form? zloc)
             (required-prefix? context "reitit.frontend")
             (required? context 'io.factorhouse.rfx.core)
             (ns-source-includes? context "dispatch"))
    {:rule-id :reitit/frontend-rfx-dispatch-boundary
     :message "Frontend routing namespaces that dispatch RFX events should expose the routing/state boundary"
     :suggested-source "route match -> named navigation event"
     :contract {:blocked-by ["Routing ownership and event naming depend on the application shell."]
                :llm-task "Review this frontend routing namespace and make the route-to-RFX dispatch boundary explicit through named events or a small adapter."
                :acceptance ["Route matching and event dispatch boundaries are named."
                             "Navigation behavior is unchanged."
                             "Frontend routing smoke checks pass."]}}))

(defn- integrant-alias [context]
  (alias-for context 'integrant.core))

(defn- integrant-head? [context head target-name full-symbol]
  (head-matches? head (integrant-alias context) target-name full-symbol))

(defn- integrant-init-key-match [zloc context]
  (when (and (required? context 'integrant.core)
             (helpers/list-form? zloc))
    (let [head (helpers/head-symbol zloc)
          [_ _ key-loc] (helpers/child-locs zloc)
          key-form (some-> key-loc z/sexpr)
          source-text (:source context)]
      (when (and (= 'defmethod head)
                 (some? key-form)
                 (integrant-head? context (some-> (helpers/child-loc zloc 1) z/sexpr) "init-key" 'integrant.core/init-key)
                 (not (str/includes? source-text (str "halt-key! " key-form))))
        {:rule-id :integrant/init-halt-symmetry
         :message "Integrant init-key methods should be reviewed for matching halt-key! ownership"
         :suggested-source "(defmethod ig/halt-key! key [_ value] ...)"
         :contract {:blocked-by ["Some components do not need halt behavior, but that should be an explicit lifecycle decision."]
                    :llm-task "Review this Integrant init-key method and add a matching halt-key! or document why no halt is required."
                    :acceptance ["Lifecycle ownership is explicit."
                                 "Resources that need cleanup are halted idempotently."
                                 "System start/stop tests pass."]}}))))

(defn- integrant-key-alignment-match [zloc context]
  (when (and (required? context 'integrant.core)
             (helpers/list-form? zloc)
             (= 'defmethod (helpers/head-symbol zloc)))
    (let [[_ dispatch-loc key-loc] (helpers/child-locs zloc)
          dispatch-form (some-> dispatch-loc z/sexpr)
          key-form (some-> key-loc z/sexpr)]
      (when (and (integrant-head? context dispatch-form "init-key" 'integrant.core/init-key)
                 (keyword? key-form)
                 (nil? (namespace key-form)))
        {:rule-id :integrant/config-key-namespace-alignment
         :message "Integrant config keys should usually be namespaced to their component boundary"
         :suggested-source "::component-key"
         :contract {:blocked-by ["The correct key namespace depends on module and config ownership."]
                    :llm-task "Review this Integrant config key and align it with the component namespace or documented module boundary."
                    :acceptance ["Config keys are namespaced or intentionally documented."
                                 "Config data and defmethod dispatch keys still match."
                                 "System tests pass."]}}))))

(defn- integrant-ref-match [zloc context]
  (when (and (required? context 'integrant.core)
             (helpers/list-form? zloc)
             (integrant-head? context (helpers/head-symbol zloc) "ref" 'integrant.core/ref))
    {:rule-id :integrant/ref-graph-boundary
     :message "Integrant refs should be reviewed for startup graph clarity and hidden global boundaries"
     :suggested-source "(ig/ref ::dependency)"
     :contract {:blocked-by ["Dependency graph shape is a system-level design decision."]
                :llm-task "Review this Integrant reference graph for fan-out, hidden globals, and startup boundary clarity."
                :acceptance ["Component dependencies are explicit."
                             "Startup ordering remains correct."
                             "System graph tests or start/stop smoke checks pass."]}}))

(defn- integrant-expand-key-match [zloc context]
  (when (and (required? context 'integrant.core)
             (helpers/list-form? zloc)
             (= 'defmethod (helpers/head-symbol zloc)))
    (let [dispatch-form (some-> (helpers/child-loc zloc 1) z/sexpr)]
      (when (integrant-head? context dispatch-form "expand-key" 'integrant.core/expand-key)
        {:rule-id :integrant/module-expansion-shape
         :message "Integrant expand-key methods should be reviewed for module boundary clarity"
         :suggested-source "(defmethod ig/expand-key ::module [_ opts] ...)"
         :contract {:blocked-by ["Module expansion shape depends on reusable config boundaries."]
                    :llm-task "Review this Integrant expand-key method and make hidden component responsibilities visible in named config keys or helper functions."
                    :acceptance ["Expanded keys are predictable and namespaced."
                                 "Module responsibilities are documented by data shape."
                                 "System expansion tests pass."]}}))))

(defn- malli-project? [context]
  (required-prefix? context "malli."))

(defn- malli-alias [context]
  (alias-for context 'malli.core))

(defn- malli-head? [context head target-name full-symbol]
  (head-matches? head (malli-alias context) target-name full-symbol))

(defn- malli-schema-vector? [form]
  (and (vector? form)
       (keyword? (first form))
       (not (namespace (first form)))))

(defn- malli-large-inline-schema-match [zloc context]
  (when (and (malli-project? context)
             (vector-form? zloc))
    (let [form (z/sexpr zloc)]
      (when (and (malli-schema-vector? form)
                 (>= (count form) 4))
        {:rule-id :malli/large-inline-schema
         :message "Large inline Malli schemas should be reviewed for named reusable placement"
         :suggested-source "(def UserSchema [:map ...])"
         :contract {:blocked-by ["Schema ownership depends on domain and boundary reuse."]
                    :llm-task "Extract or justify this inline Malli schema so reusable domain schemas and IO-boundary schemas have predictable names."
                    :acceptance ["Schema placement follows domain or boundary ownership."
                                 "Validation semantics are unchanged."
                                 "Schema tests pass."]}}))))

(defn- malli-function-schema-match [zloc context]
  (when (and (malli-project? context)
             (helpers/list-form? zloc))
    (let [head (helpers/head-symbol zloc)]
      (when (malli-head? context head "=>" 'malli.core/=>)
        {:rule-id :malli/function-schema-instrumentation
         :message "Malli function schemas should be reviewed with instrumentation and boundary policy"
         :suggested-source "(m/=> #'var [:=> ...])"
         :contract {:blocked-by ["Instrumentation timing differs between dev, test, and production."]
                    :llm-task "Review this Malli function schema and ensure instrumentation policy is explicit for dev/test/prod."
                    :acceptance ["Function schema intent is clear."
                                 "Instrumentation setup is documented or tested."
                                 "Function schema tests pass."]}}))))

(defn- malli-registry-match [zloc context]
  (when (and (malli-project? context)
             (or (vector-form? zloc) (map-form? zloc)))
    (let [form (z/sexpr zloc)]
      (when (or (and (vector? form) (= :ref (first form)))
                (and (map? form) (contains? form :registry)))
        {:rule-id :malli/registry-recursive-reference
         :message "Malli registries and recursive references should be reviewed for stable names"
         :suggested-source "{:registry {...}} / [:ref ::schema]"
         :contract {:blocked-by ["Registry organization and recursive schema names are project-level choices."]
                    :llm-task "Review Malli registry and recursive references so schema names are stable, namespaced, and easy to navigate."
                    :acceptance ["Recursive references resolve through a clear registry."
                                 "Schema names are stable and namespaced."
                                 "Schema expansion/validation tests pass."]}}))))

(defn- schema-boundary-validation-match [zloc context]
  (when (and (malli-project? context)
             (helpers/list-form? zloc))
    (let [head (helpers/head-symbol zloc)]
      (when (malli-head? context head "validate" 'malli.core/validate)
        {:rule-id :schema/boundary-validation
         :message "Schema validation calls should be reviewed for IO boundary placement"
         :suggested-source "(validate-at-boundary schema value)"
         :contract {:blocked-by ["Validation placement depends on API, storage, and UI boundary ownership."]
                    :llm-task "Review this validation call and move or name it so schema validation happens at the intended IO boundary."
                    :acceptance ["Validation still protects the same boundary."
                                 "Error reporting semantics are unchanged."
                                 "Boundary tests pass."]}}))))

(defn- reitit-malli-coercion-match [zloc context]
  (when (and (required-prefix? context "reitit.")
             (malli-project? context)
             (map-form? zloc))
    (let [form (z/sexpr zloc)]
      (when (and (or (contains? form :parameters)
                     (contains? form :responses))
                 (not (contains? form :coercion)))
        {:rule-id :reitit/malli-coercion-integration
         :message "Reitit routes with Malli parameters or responses should make coercion placement explicit"
         :suggested-source "{:coercion reitit.coercion.malli/coercion ...}"
         :contract {:blocked-by ["Coercion may be inherited from router defaults, route data, or middleware."]
                    :llm-task "Review this Reitit/Malli route data and make coercion ownership explicit at router or route level."
                    :acceptance ["Coercion source is visible."
                                 "Request and response schemas still validate."
                                 "Route coercion tests pass."]}}))))

(defn- http-schema-coupling-match [zloc context]
  (when (and (or (required-prefix? context "ring.")
                 (required-prefix? context "reitit."))
             (malli-project? context)
             (helpers/list-form? zloc)
             (= 'defn (helpers/head-symbol zloc)))
    (let [[_ name-loc] (helpers/child-locs zloc)
          name-form (some-> name-loc z/sexpr)
          form (z/sexpr zloc)]
      (when (and (symbol? name-form)
                 (re-find #"handler|endpoint|route" (name name-form))
                 (contains-call-head? form #{'malli.core/validate
                                             (symbol (str (malli-alias context)) "validate")}))
        {:rule-id :http/schema-boundary-coupling
         :message "HTTP handlers with inline schema validation should be reviewed for boundary coupling"
         :suggested-source "named request/response schema boundary"
         :contract {:blocked-by ["The right split depends on API boundary ownership and error formatting."]
                    :llm-task "Review this HTTP handler and separate route data, validation, and handler logic when it improves boundary clarity."
                    :acceptance ["Handler behavior and error format are unchanged."
                                 "Schema validation has a named boundary."
                                 "HTTP tests pass."]}}))))

(defn- first-match [zloc context]
  (or (namespace-responsibility-match zloc context)
      (hiccup-attribute-shape-match zloc context)
      (reagent-atom-match zloc context)
      (reagent-form2-match zloc context)
      (re-frame-reg-event-db-match zloc context)
      (re-frame-reg-sub-match zloc context)
      (re-frame-namespace-placement-match zloc context)
      (react-hook-match zloc context)
      (hsx-inline-component-match zloc context)
      (ring-response-map-match zloc context)
      (ring-middleware-match zloc context)
      (reitit-route-inline-handler-match zloc context)
      (reitit-middleware-coercion-match zloc context)
      (reitit-frontend-rfx-match zloc context)
      (integrant-init-key-match zloc context)
      (integrant-key-alignment-match zloc context)
      (integrant-ref-match zloc context)
      (integrant-expand-key-match zloc context)
      (malli-large-inline-schema-match zloc context)
      (malli-function-schema-match zloc context)
      (malli-registry-match zloc context)
      (schema-boundary-validation-match zloc context)
      (reitit-malli-coercion-match zloc context)
      (http-schema-coupling-match zloc context)))

(def rule
  {:id :framework/contracts
   :summary "Emit framework and architecture contracts for CLJS, HTTP, schema, and system lifecycle boundaries"
   :safety :unsafe
   :tier :llm-refactor
   :kinds #{:rewrite}
   :check (fn [zloc]
            (or (helpers/list-form? zloc)
                (vector-form? zloc)
                (map-form? zloc)))
   :apply (fn [zloc context]
            (if-let [{:keys [rule-id message suggested-source contract]} (first-match zloc context)]
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
