# Framework Profiles

Framework profiles are Formsmith's detection layer for framework-aware style
work. They detect active library surfaces from namespace facts and attach
canonical guidance to the report.

Profiles are informational by themselves. Framework enforcement is exposed as
report-only `llm-refactor` contracts unless a rewrite is local enough for
ordinary autofix. Profiles are used for:

- making the active framework surface visible in CI/debug output
- producing structured LLM refactor contracts where behavior-preserving autofix
  is not local
- gating framework-specific local rewrites where the import shape proves the
  target library is present

## Commands

```bash
clojure -M -m formsmith.main profiles src test
clojure -M -m formsmith.main profiles --json src test
clojure -M -m formsmith.main contracts src test
clojure -M -m formsmith.main contracts --json src test
```

## Current Profiles

| Profile | Category | Detection examples |
| --- | --- | --- |
| `re-frame` | `cljs-state` | `re-frame.core` |
| `reagent` | `cljs-view` | `reagent.core` |
| `hsx` | `cljs-view` | `io.factorhouse.hsx.core` |
| `rfx` | `cljs-state` | `io.factorhouse.rfx.core` |
| `clojuredart` | `mobile-ui` | `cljd.flutter`, `package:flutter/...` string requires |
| `ring` | `http` | `ring.util.response` |
| `reitit` | `routing` | `reitit.ring` |
| `integrant` | `system` | `integrant.core` |
| `malli` | `schema` | `malli.core` |

## Cross-Framework Contracts

The `framework/contracts` rule family emits contracts for boundary decisions
that are useful to standardize but too behavior-sensitive for no-context
autofix.

Current cross-framework contracts:

- `namespace/mixed-framework-responsibility`
  Namespaces that mix several framework categories are flagged for
  responsibility-boundary review.
- `hiccup/attribute-shape`
  Hiccup class strings are flagged for a project-local class composition
  convention.
- `reagent/local-ratom-state`
  Reagent local ratoms are flagged for state ownership and lifetime review.
- `reagent/form-2-component`
  Reagent form-2 components are flagged for explicit lifetime intent.
- `re-frame/reg-event-db-side-effect`
  `reg-event-db` handlers containing side effects are flagged for
  `reg-event-fx` or effect extraction.
- `re-frame/reg-sub-signals-shape`
  `reg-sub` signal functions are flagged for subscription graph readability.
- `re-frame/mixed-registration-namespace`
  Namespaces mixing events, effects, and subscriptions are flagged for
  placement review.
- `ring/raw-response-map`
  Raw Ring response maps are flagged for response helper consistency.
- `ring/middleware-shape`
  Ring middleware definitions are flagged for wrapping, arity, and order review.
- `reitit/inline-route-handler`
  Inline route handlers are flagged for named handler boundary review.
- `reitit/middleware-coercion-placement`
  Reitit middleware and coercion placement are flagged together.
- `reitit/frontend-rfx-dispatch-boundary`
  Frontend routing namespaces that dispatch RFX events are flagged for an
  explicit route-to-state boundary.
- `integrant/init-halt-symmetry`
  `init-key` methods without matching halt ownership are flagged.
- `integrant/config-key-namespace-alignment`
  Unqualified Integrant config keys are flagged for namespace alignment.
- `integrant/ref-graph-boundary`
  `ig/ref` usage is flagged for startup graph clarity.
- `integrant/module-expansion-shape`
  `expand-key` methods are flagged for module boundary review.
- `malli/large-inline-schema`
  Large inline Malli schemas are flagged for reusable placement review.
- `malli/function-schema-instrumentation`
  Malli function schemas are flagged for instrumentation policy review.
- `malli/registry-recursive-reference`
  Malli registries and recursive references are flagged for stable naming.
- `schema/boundary-validation`
  Schema validation calls are flagged for IO boundary placement.
- `reitit/malli-coercion-integration`
  Reitit routes with Malli parameters or responses are flagged when coercion
  ownership is not visible on the route data.
- `http/schema-boundary-coupling`
  HTTP handlers with inline schema validation are flagged for route, schema, and
  handler boundary review.

## HSX And RFX

HSX/RFX support starts with contracts rather than autofix. These findings are
reported as `llm-refactor` because the better shape depends on application
boundaries.

Current contracts:

- `hsx/local-ratom-state`
  HSX namespaces that still create local Reagent ratom state are flagged for a
  React-state review.
- `hsx/reagent-as-element-entrypoint`
  Reagent `as-element` entrypoints inside HSX projects are flagged for
  `hsx/create-element` migration review.
- `hsx/reagent-class-component`
  Reagent `create-class` components inside HSX projects are flagged for React
  function-component migration review.
- `hsx/react-hook-deps`
  HSX React effect hooks without dependency vectors are flagged for dependency
  decision review.
- `hsx/inline-component-identity`
  Inline anonymous HSX components are flagged for stable identity and
  memoization boundary review.
- `rfx/implicit-global-dispatch`
  One-argument `rfx/dispatch` calls are flagged for context-boundary review.
- `rfx/reg-sub-signals-function`
  re-frame-style subscription signal functions are flagged because RFX expects
  its supported dependency sugar instead.
- `rfx/reg-fx-handler-arity`
  one-argument RFX effect handlers are flagged for the explicit RFX instance
  plus effect value handler shape.

## ClojureDart

Formsmith scans `.cljd` files and understands ClojureDart namespace requires,
including string requires such as:

```clojure
(ns app.mobile
  (:require ["package:flutter/material.dart" :as m]
            [cljd.flutter :as f]))
```

The first ClojureDart autofix is intentionally local and visible. Long nested
single-child Flutter constructor chains can be rewritten to `f/nest` when the
file explicitly aliases the current `cljd.flutter` namespace:

```clojure
(m/IgnorePointer
  .ignoring disabled?
  .child
  (m/AnimatedContainer
    .duration duration
    .child
    (m/FloatingActionButton
      .onPressed submit
      .child
      (m/Icon m/Icons.create))))
```

becomes:

```clojure
(f/nest
  (m/IgnorePointer .ignoring disabled?)
  (m/AnimatedContainer .duration duration)
  (m/FloatingActionButton .onPressed submit)
  (m/Icon m/Icons.create))
```

Safety boundary:

- `clojuredart/widget-child-chain` is a default-safe local rewrite only when the
  current `cljd.flutter` alias is explicit.
- `clojuredart/deprecated-flutter-alpha` is report-only. Deprecated
  `cljd.flutter.alpha` and `cljd.flutter.alpha2` namespaces are not rewritten
  automatically.
- `clojuredart/widget-local-atom-state`,
  `clojuredart/controller-without-widget-with`,
  `clojuredart/animation-controller-ticker`,
  `clojuredart/widget-of-context-helper`,
  `clojuredart/builder-to-widget`, and
  `clojuredart/widget-body-extraction` are report-only contracts. They point at
  `f/widget` state, lifecycle, context, builder, and extraction decisions that
  need surrounding-code judgment.
- ClojureDart type annotations such as `^#/(m/Animation double)` are tolerated
  by traversal; sexpr-dependent Clojure rules skip unsupported nodes instead of
  crashing the run.

## Demo Fixture

The bundled `.cljd` fixture shows the visible rewrite:

```bash
clojure -M -m formsmith.main fix --no-config --check --rewrite-only corpus/clojuredart/widget-child-chain.before.cljd
```

Expected summary:

```text
files=1 changed=1 findings=1
```
