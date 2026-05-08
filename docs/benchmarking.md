# Benchmarking

## Why

`formsmith` should not optimize only for hand-made toy examples.

The real target is code that:

- already runs
- already passes formatter and bug lint
- still feels shallow, unidiomatic, or structurally weak

## Benchmark Shelves

### corpus

Small, deterministic fixtures for rule correctness.

Use for:

- parser safety
- rewrite correctness
- patch quality regression checks

### ugly corpus

Human-plausible code smells that still fit within local beautification.

Use for:

- stacked rewrites
- layout-sensitive cases
- conservative autofix boundaries

### dirty zoo

Whole repos, especially model-generated repos.

Use for:

- post-lint, post-format signal quality
- safe fix hit rate
- CLJ versus CLJS rule balance
- "does this still matter after other tools already ran?"

Whole-repo benchmarks can play two different roles:

- clean benchmark
  the repo passes ordinary quality gates and is valid direct evidence for the narrow `v1` wedge
- pressure repo
  the repo is already useful for signal pressure, but still carries enough ordinary-tool noise that it should not be treated as the cleanest benchmark evidence yet

## Dirty Zoo Intake Criteria

Prefer repos that:

- have both backend and frontend when possible
- already pass ordinary quality gates
- still show visible Clojure taste gaps
- represent different model and human styles

## Current Studio Loop

The active flagship question is whether the current `v1` wedge still beats the ordinary formatter-plus-bug-lint stack on code that already passes those tools.

Run the current benchmark loop with:

```bash
bb benchmark-v1
```

This is a maintainer evidence packet. It is not required for the repo-only
public first run.

This executes:

- pure `formsmith` signal on `nextmoon`
- pure `formsmith` signal on `dirty-clojure`
- safe rewrite-only previews on both repos
- focused aggressive CLJS previews on both repos

The task expects sibling checkouts next to this repository:

- `../nextmoon`
- `../dirty-clojure`

You can override those with `NEXTMOON_DIR` and `DIRTY_CLOJURE_DIR` if needed.

Run this loop whenever:

- rule behavior changes
- rewrite trust or patch locality changes
- benchmark docs or benchmark numbers change
- the public product claim is being widened

Run the stronger validation packet with:

```bash
bb validate-v1
```

Use that packet when deciding whether the current maintainer benchmark claim is
still supported. It expects the same sibling benchmark checkouts. Use
`bb validate-cold-start` for the self-contained public clone first-run
evidence.

Current examples to keep in the loop:

- `dirty-clojure`
- `nextmoon`

Current read on those examples:

- `dirty-clojure` is the current clean maintainer benchmark repo:
  - `npm test` passes
  - `npm run lint` passes
  - `npm run format:check` passes
  - `formsmith` still yields meaningful local findings after those gates
- `nextmoon` is now also a clean maintainer benchmark repo:
  - `clojure -M:test` passes
  - ad hoc `clj-kondo` on `src test deps.edn shadow-cljs.edn` passes clean
  - ad hoc `cljfmt check` on `src test deps.edn shadow-cljs.edn` passes clean
  - `formsmith` still yields useful local and CLJS-facing findings after those gates
- `nextmoon` still matters as the stronger pressure surface:
  - it is a real app repo, not only a generated proving repo
  - cleanup reduced the signal from `17` to `12`, which is useful evidence that the remaining findings are tighter than before
- the current CLJS benchmark loop is now strong enough to close `M2`:
  - `dirty-clojure/frontend/core.cljs` gives an `--aggressive --rewrite-only` preview with `files=1 changed=1 findings=5`
  - `nextmoon/ui/core.cljs` gives an `--aggressive --rewrite-only` preview with `files=1 changed=1 findings=1`
  - both repos yield repeated `.cljs` findings from frontend-local idioms rather than parser accidents
- the current fix-trust loop is now strong enough to close `M3`:
  - `nextmoon` safe rewrite-only preview lands at `files=18 changed=1 findings=1`
  - `dirty-clojure` safe rewrite-only preview lands at `files=9 changed=1 findings=1`
  - repo-wide `--check` now surfaces only rewrites that would actually apply

## Evaluation Questions

For each benchmark repo, ask:

1. Does `formsmith` find anything meaningful after formatter and `clj-kondo`?
2. Are the safe fixes legible and local?
3. Does CLJS get real value, or only CLJ?
4. Are the findings about genuine taste, not mere novelty?
5. Would a human reviewer actually agree with the output?

## Minimum v1 Scorecard

- at least two clean benchmark repos that still yield useful findings after ordinary tooling
- at least one repo that acts as a stronger real-world pressure surface
- benchmark checks run often enough to block regressions in patch quality
