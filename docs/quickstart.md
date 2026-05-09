# Quickstart

## What `formsmith` Is

`formsmith` is a post-format, post-lint beauty pass.

Use it after the code already:

- compiles
- passes tests
- is formatted
- passes ordinary lint

The goal is the next step:

- stronger local Clojure idioms
- safer and more legible rewrites
- one more pass before review

## Requirements

- JDK
- Clojure CLI

If you are using an AI coding agent, read `AGENTS.md` too. It tells the agent which packet to run and how to preserve the current trust boundary.

For another repo, install `formsmith` through a Clojure CLI git dependency.
See [install](./install.md).

## First Run

From the repo root:

```bash
clojure -M -m formsmith.main lint src test
```

This shows the full surface, including merged `clj-kondo` findings when enabled.

If you want only the native `formsmith` signal:

```bash
clojure -M -m formsmith.main lint --no-kondo src test
```

## Safe Preview

Before writing anything:

```bash
clojure -M -m formsmith.main check .
```

`check` is the canonical CI-facing no-write command. It exits non-zero when
canonical safe rewrites would change files.

The older explicit spelling is still available:

```bash
clojure -M -m formsmith.main fix --check .
```

This uses the same default no-write preview for safe rewrites.

If you want to inspect rewrite locality without the formatter pass:

```bash
clojure -M -m formsmith.main fix --check --rewrite-only .
```

If you are adopting `formsmith` in an existing repo and want CI to block only
new findings, create a baseline first:

```bash
clojure -M -m formsmith.main baseline src test -o .formsmith-baseline.edn
```

Then reference it from `.formsmith.edn`:

```clojure
{:baseline ".formsmith-baseline.edn"}
```

## Apply Safe Rewrites

```bash
clojure -M -m formsmith.main fix .
```

Default `fix` only applies `syntax-safe` rewrites.

## Apply Aggressive Rewrites

Preview analyzer-guarded rewrites first:

```bash
clojure -M -m formsmith.main fix --check --guarded .
```

`--guarded` uses analyzer facts before applying supported analyzer-guarded
rewrites. The current guarded surface is intentionally narrow.

Preview first:

```bash
clojure -M -m formsmith.main fix --check --aggressive .
```

Then apply:

```bash
clojure -M -m formsmith.main fix --aggressive .
```

Use this when you want `semantic-pattern` rewrites too, not only the conservative default set.

## LLM Refactor Contracts

```bash
clojure -M -m formsmith.main contracts src test
clojure -M -m formsmith.main contracts --json src test
```

Use this when `formsmith` can identify a likely better shape but cannot prove a
mechanical rewrite is behavior-preserving. The output is intended for an LLM or
human refactor pass, not for direct autofix. Successful export exits `0` even
when contracts are present; use `check` when you need a CI gate.

JSON output omits source-heavy fields by default. Add `--include-source` only
when you intentionally want full source snippets in a local debugging artifact.

## Format Only

```bash
clojure -M -m formsmith.main fmt src test
```

This runs the formatter boundary without semantic rewrites.

## Rule Discovery

List the current catalog:

```bash
clojure -M -m formsmith.main rules
```

Explain one rule:

```bash
clojure -M -m formsmith.main explain condition/and-seq
```

## Analyzer Facts

Inspect namespace and var-level facts:

```bash
clojure -M -m formsmith.main analyze src test
```

This is the foundation for analyzer-guarded rewrites and framework-aware
contracts. The analyzer output itself is informational.

## Framework Profiles

```bash
clojure -M -m formsmith.main profiles src test
clojure -M -m formsmith.main profiles --json src test
```

Profiles detect known framework surfaces such as re-frame, Reagent, HSX, RFX,
ClojureDart, Ring, Reitit, Integrant, and Malli from namespace facts. The
profile output is informational; framework enforcement appears as report-only
contracts unless a target is local enough for safe autofix.

ClojureDart support also includes `.cljd` scanning, string-package requires,
and the first visible Flutter UI rewrite from nested `.child` chains to
`cljd.flutter/nest` when the current Flutter helper alias is explicit.

See [framework profiles](./framework-profiles.md) for the current profile and
contract surface.

## Recommended Daily Loop

1. `lint`
2. `check`
3. `fix`
4. `fix --guarded --check` when you want analyzer-backed rewrites
5. `fix --aggressive --check` when you want a deeper pass
6. `fix --aggressive` only after the preview looks natural

## Repo-Only Demo

If you want a true repo-only first run, without relying on sibling benchmark repos:

```bash
bash scripts/cold-start-demo.sh prepare
bash scripts/cold-start-demo.sh lint
bash scripts/cold-start-demo.sh check
bash scripts/cold-start-demo.sh preview
```

This uses a bundled disposable sample target inside the repo itself.

See [cold-start demo](./cold-start-demo.md) for the full loop.

Maintainers can verify that this documented path still works from a fresh disposable repo copy with:

```bash
bb validate-cold-start
```

## Current Trust Boundary

- default `fix` is conservative
- `check` is the CI-facing canonical no-write command
- `.formsmith.edn` can ignore generated paths, exclude rules, and point at a
  baseline file
- `baseline` lets existing projects adopt CI without paying down every old
  finding first
- `--guarded` applies only supported analyzer-backed rewrites
- `--check` shows only findings that would actually apply
- `--rewrite-only` is the best way to inspect patch locality
- `--no-config` is useful for corpus/demo validation where the repo config would
  intentionally ignore dirty fixtures
- comment-sensitive forms are kept conservative through protected corpus coverage
