# formsmith

`formsmith` is a Clojure-native beauty pass for code that already runs, already formats cleanly, and still reads a little shallow.

Formatters normalize shape. Bug linters point at correctness issues. `formsmith` sits after both. It rewrites code at the form level, then formats the result, so one pass can make code not only cleaner, but more Clojure-like.

## Status

Pre-beta, but already useful on real repos.

Current `v1` scope is deliberately narrow:

- config-free by default
- conservative autofix first
- CLJ and CLJS local style coverage
- `clj-kondo` findings on the same lint surface when desired

The current milestone question is:

> after formatter and bug lint already passed, does the current `v1` wedge still produce enough safe, explainable improvement to justify a broader public pre-beta share?

That means the project posture is currently:

- repo-first pre-beta
- versioned GitHub source release
- short-lived branches and PRs for mergeable work
- `bb ci` as the default code-change gate
- `bb benchmark-v1` when rewrite behavior or public claims move
- `bb validate-v1` when deciding whether the public wedge claim is actually proven
- `bb validate-cold-start` when the repo-only first-run path or onboarding docs move
- best-effort support while the wedge is still being proven

See [contributing](./CONTRIBUTING.md).

Current source release: `v0.1.0-pre.4` at
`a2449935781fc6f3b2b4451aa3e8e30e0bbc9abf`.

If you are operating the repo through an AI coding agent, use [AGENTS.md](./AGENTS.md) as the public AI operator guide for cold sessions and packet selection.

## Why Use This After `clj-kondo` And A Formatter?

Because there is still a gap after ordinary tooling passes.

That gap looks like this:

- `if` / `when` / `cond` forms that work but are weaker than the idiomatic shape
- `seq`-based emptiness checks that want `not-empty`, `if-let`, or `when-let`
- blankness checks that want `blank?`
- local wrappers like nested `let`, redundant `do`, redundant `str`, or `when (not ...)`

`formsmith` is not trying to replace every formatter or every linter. It is trying to own that last mile.

## Quickstart

Requirements:

- JDK
- Clojure CLI

Maintainer checks also use Babashka. Proof verification uses Lean, or Nix to
run Lean, through `scripts/verify-proofs.sh`.

Run from the repo:

```bash
clojure -M -m formsmith.main lint src test
clojure -M -m formsmith.main check .
clojure -M -m formsmith.main fix --check .
clojure -M -m formsmith.main fix --check --guarded .
clojure -M -m formsmith.main fix .
clojure -M -m formsmith.main fix --aggressive .
clojure -M -m formsmith.main baseline src test -o .formsmith-baseline.edn
```

For another repo, add a `:formsmith` alias that consumes the GitHub source
release. See [install](./docs/install.md) and [CI](./docs/ci.md).

If you want only `formsmith` findings without merged `clj-kondo` output:

```bash
clojure -M -m formsmith.main lint --no-kondo src test
```

If you want a repo-only first run without any sibling benchmark repos:

```bash
bash scripts/cold-start-demo.sh prepare
bash scripts/cold-start-demo.sh lint
bash scripts/cold-start-demo.sh preview
```

Maintainers can verify that repo-only path from a fresh disposable workspace copy with:

```bash
bb validate-cold-start
```

## Recommended Workflow

1. Run `lint` to see the full surface.
2. Run `check` to preview the canonical safe rewrites that CI can enforce.
3. Run `fix` when the safe preview looks good.
4. Run `fix --guarded --check` when you want analyzer-backed rewrites whose local-symbol guards are proven by static facts.
5. Run `fix --aggressive --check` before applying broader semantic-pattern rewrites repo-wide.
6. Use `baseline` when introducing `formsmith` into an existing repo and you want CI to fail only on new findings.
7. Use `rules` and `explain <rule-id>` when you want the catalog and safety boundary.

## Production Adoption Surface

`formsmith` reads `.formsmith.edn` by default when it exists. The current
minimum production config surface is intentionally small:

```clojure
{:ignore-paths ["target" "resources/generated"]
 :rules {:exclude [:some/rule]}
 :baseline ".formsmith-baseline.edn"
 :suppressions [{:file "src/app/core.clj"
                 :rule-id :if/not-condition
                 :line 42}]}
```

Inline suppressions are supported for the rare case where a local idiom is
intentional:

```clojure
;; formsmith-disable-next-line if/not-condition
(if (not ready?) :wait :go)
```

Default JSON output omits full file source and before/after snippets so CI
artifacts are safer for proprietary repos. Use `--include-source` only for local
debugging.

## Safety Model

- `syntax-safe`
  Local rewrites that `formsmith` is willing to auto-apply in default `fix`.
- `semantic-pattern`
  Strong suggestions that need `--aggressive` for autofix.
- `unsafe`
  Report only. Useful signal, but not something the tool should rewrite by default.

Two important commands:

- `fix --check`
  No-write preview. Reports only findings that would actually apply.
- `check`
  Canonical no-write check for CI. Equivalent to the default safe autofix profile without writing.
- `fix --guarded`
  Apply analyzer-guarded rewrites only when static facts prove the current supported guard.
- `fix --rewrite-only`
  Skip the formatter phase. Useful when you want to inspect rewrite locality directly.

## Benchmark Snapshot

Current snapshot on `main` as of `2026-05-08`:

- pure `formsmith` lint on `nextmoon/src`: `files=18 changed=0 findings=12`
- pure `formsmith` lint on `dirty-clojure/src test`: `files=9 changed=0 findings=7`
- safe preview on `nextmoon/src`: `fix --check --rewrite-only` => `files=18 changed=1 findings=1`
- safe preview on `dirty-clojure/src test`: `fix --check --rewrite-only` => `files=9 changed=1 findings=1`
- CLJS-only aggressive preview:
  - `dirty-clojure/frontend/core.cljs` => `files=1 changed=1 findings=5`
  - `nextmoon/ui/core.cljs` => `files=1 changed=1 findings=1`

Those numbers matter because both benchmark repos now pass their baseline tests, lint, and format checks before `formsmith` adds additional signal.

The current public proof is now stronger:

- `dirty-clojure` is a clean proof repo with repeated CLJ and CLJS findings
- `nextmoon` is also now a clean proof repo, while still acting as the stronger real-repo pressure surface

That means `formsmith` now has two clean benchmark repos showing that meaningful local improvements remain after ordinary tooling passes.

Refresh the current matrix with:

```bash
bb benchmark-v1
```

Verify the actual wedge evidence with:

```bash
bb validate-v1
```

Those two evidence packets are maintainer commands and expect sibling benchmark
checkouts for `nextmoon` and `dirty-clojure`. A fresh public clone can verify
the bundled first-run path with `bb validate-cold-start`.

## Commands

```bash
clojure -M -m formsmith.main lint <paths...>
clojure -M -m formsmith.main check <paths...>
clojure -M -m formsmith.main fix <paths...>
clojure -M -m formsmith.main fmt <paths...>
clojure -M -m formsmith.main analyze <paths...>
clojure -M -m formsmith.main profiles <paths...>
clojure -M -m formsmith.main contracts <paths...>
clojure -M -m formsmith.main rules
clojure -M -m formsmith.main explain <rule-id>
```

Examples:

```bash
clojure -M -m formsmith.main fix --check --rewrite-only src test
clojure -M -m formsmith.main check src test
clojure -M -m formsmith.main fix --check --guarded src test
clojure -M -m formsmith.main fix --aggressive src test
clojure -M -m formsmith.main explain condition/and-seq
clojure -M -m formsmith.main analyze src test
clojure -M -m formsmith.main profiles src test
clojure -M -m formsmith.main contracts src test
clojure -M -m formsmith.main baseline src test -o .formsmith-baseline.edn
```

## Current Rule Surface

Current rule families include:

- redundant wrappers: `redundant-do`, nested `let`, redundant `str`
- conditional cleanup: `when-not`, `if-not`, `cond -> if/when`, `cond true -> :else`
- nil predicates: `= nil` -> `nil?`, `not= nil` -> `some?`
- keyword lookup: `(get m :k)` -> `(:k m)`
- emptiness and blankness: `seq`, `not-empty`, `blank?`
- CLJS-facing local patterns around frontend form and collection checks

See the live catalog:

```bash
clojure -M -m formsmith.main rules
clojure -M -m formsmith.main explain cond/minimal-form
```

## Project Boundary

`v1` is intentionally not:

- a formatter war
- a universal bug linter
- a repo architecture analyzer
- a config-heavy style language

The current wedge is simpler:

> after formatter and bug lint already passed, can one more tool still make the code more Clojure-like in a safe, explainable way?

That is what `formsmith v1` is trying to prove.

## Docs

- [vision](./docs/vision.md)
- [architecture](./docs/architecture.md)
- [AI operator guide](./AGENTS.md)
- [install](./docs/install.md)
- [CI](./docs/ci.md)
- [adoption trial](./docs/adoption-trial.md)
- [release notes](./docs/releases/v0.1.0-pre.4.md)
- [proofs](./docs/proofs.md)
- [v1 validation](./docs/v1-validation.md)
- [quickstart](./docs/quickstart.md)
- [cold-start demo](./docs/cold-start-demo.md)
- [cold-start adoption](./docs/cold-start-adoption.md)
- [benchmarking](./docs/benchmarking.md)
- [corpus](./docs/corpus.md)
- [pressure projects](./docs/pressure-projects.md)
- [field trials](./docs/field-trials.md)
