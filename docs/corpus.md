# Corpus Strategy

`formsmith` needs two kinds of examples:

- idealized examples that isolate one rule cleanly
- intentionally ugly examples that resemble code people actually leave behind

The second category matters because a style rewrite tool does not earn trust by only winning on textbook inputs.

## Ugly Corpus Principles

- Prefer human-plausible ugliness over random broken code.
- Keep fixtures self-contained so lint noise does not drown the target style behavior.
- Include cases that require multiple rewrites in sequence, not only one-rule demos.
- Preserve examples that still feel recoverable by a conservative tool. The corpus is for beautification pressure, not parser abuse.
- Treat `.before` files as dirty inputs and `.after` files as golden outputs. Do not rewrite fixtures in place during ordinary checks.

## Current Ugly Corpus Axes

- redundant `do`
- nested `let` that can collapse into one binding block
- redundant `str` around a single string literal
- empty `let` bindings
- `if` branches wrapped in single-expression `do`
- `cond` catch-all written as `true`
- minimal `cond` forms that should become `when` or `if`
- negated conditions that should collapse into `when-not` or `if-not`
- direct `seq` tests that should collapse into `not-empty`-based idioms
- chained cases where more than one rewrite should fire
- comment-bearing forms that should currently stay suggestion-only for safety
- semantic rewrites that should only auto-apply in aggressive mode
- report-only contract fixtures that should produce findings but intentionally
  remain unchanged
- CLJS framework boundary contracts for Hiccup, Reagent, re-frame, HSX/RFX,
  HTTP, Reitit, Malli, and Integrant
- ClojureDart `.cljd` Flutter UI chains that should normalize to
  `cljd.flutter/nest`

Over time this corpus should grow toward real-world anti-pattern shelves rather than a flat pile of random samples.

## Shelf Roles

- `corpus/basic`: small isolated examples for one rule at a time
- `corpus/ugly`: intentionally awkward but human-plausible code that should clean up well
- `corpus/aggressive`: semantic-pattern rewrites that require `fix --aggressive`
- `corpus/contracts`: report-only contracts where `.before` and `.after` are
  identical because Formsmith should emit guidance without applying a rewrite
- `corpus/protected`: cases that are still style-smelly but should remain unchanged until patch quality improves
- `corpus/clojuredart`: ClojureDart-specific UI style fixtures where `.cljd`
  source must remain parseable and visibly cleaner after the rewrite

Current examples in `protected` also include macro-step contexts where a local rewrite would be valid as an expression but invalid in the surrounding macro contract.
