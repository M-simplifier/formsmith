# Framework Profiles

Framework profiles are Formsmith's informational layer for framework-aware
style work. They detect active library surfaces from namespace facts and attach
canonical guidance to the report.

Profiles do not yet enforce framework architecture. They are used for:

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

## HSX And RFX

HSX/RFX support starts with contracts rather than autofix. These findings are
reported as `llm-refactor` because the better shape depends on application
boundaries.

Current contracts:

- `hsx/local-ratom-state`
  HSX namespaces that still create local Reagent ratom state are flagged for a
  React-state review.
- `rfx/implicit-global-dispatch`
  One-argument `rfx/dispatch` calls are flagged for context-boundary review.

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
