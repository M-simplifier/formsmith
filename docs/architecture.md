# Architecture

## Pipeline

`formsmith` operates in explicit phases:

1. source discovery
2. semantic rewrite phase
3. formatter phase
4. reporting

The rewrite phase is the product core. The formatter phase is a backend boundary.

## Core Types

- source unit: file path plus language
- finding: rule id, message, safety class, location, and whether a fix was applied
- rewrite result: updated text plus findings
- pipeline result: per-file output plus summary

## Rule Model

Rules are Clojure values with executable behavior.

- `:id`
- `:summary`
- `:safety`
- `:kinds`
- `:check`
- `:apply`

Rules may emit a finding without rewriting. This keeps suggestion-only rules in the same model as safe autofixes.

## Analyzer Boundary

`clj-kondo` integration sits behind its own namespace boundary.

Near-term use:

- import diagnostics
- enrich findings
- expose a combined report
- expose analyzer facts through `formsmith analyze`

Longer-term use:

- rule predicates backed by static analysis
- macro-aware style rules
- code action export

The analyzer fact model currently exposes:

- namespace definitions
- namespace dependency edges
- var definition counts
- var usage counts

This is intentionally smaller than the full `clj-kondo` analysis map. The
public surface should grow around facts that `formsmith` can use for guarded
rewrites, LLM refactor contracts, and framework profiles.

The first guarded rewrite path uses local-symbol facts. It allows supported
`seq` rewrites only when the tested symbol is proven to be a local usage at the
rewrite site.

## LLM Contract Boundary

`llm-refactor` findings are not mechanical fixes. They are emitted as structured
contracts through `formsmith contracts`.

The contract output includes:

- the exact source location
- the current source fragment
- a suggested canonical target when one is known
- the reason `formsmith` did not rewrite mechanically
- the task and acceptance criteria for an LLM or human refactor pass

The first contract family covers direct `empty?` over `trim`, where `blank?` is
often preferable but nil semantics can differ.

## Framework Profile Boundary

Framework profiles are detected from analyzer namespace facts and exposed
through `formsmith profiles`.

The initial profiles cover:

- re-frame
- Reagent
- HSX
- RFX
- ClojureDart
- Ring
- Reitit
- Integrant
- Malli

Profiles emit canonical guidance and evidence. Framework-level enforcement is
separate: local ClojureDart UI rewrites can autofix, while broader CLJS, HTTP,
schema, and system lifecycle decisions are emitted as report-only
`llm-refactor` contracts.

## Formatter Boundary

The current backend is `cljfmt`, used as a tactical formatter backend so the project can ship an end-to-end wedge early.

This boundary is deliberate. If a native canonical formatter becomes necessary, the pipeline can replace the backend without changing the rewrite engine contract.

## Proof Boundary

Certified rewrites are backed by proof artifacts under `proofs/`.

The proof boundary is intentionally narrower than the runtime:

- model only the semantics needed by the certified family
- require implementation-side side conditions before emitting `certified-fix`
- keep non-certified canonical rewrites available under their own tiers
- verify proof artifacts with `bb verify-proofs`
