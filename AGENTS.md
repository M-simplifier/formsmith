# formsmith Agent Guide

## Public AI Operator Scope

This file is the public AI-facing operator guide for `formsmith`.

Use it when:

- operating this repo through an AI coding agent
- running the intended pre-beta `cold AI operator trial`
- deciding which public packet to run without relying on unpublished notes or chat history

Prefer the repo artifacts over outside notes. A cold session should be able to operate from this file plus the linked docs alone.

## Read Order For Cold Sessions

1. `README.md`
2. `docs/quickstart.md`
3. `docs/cold-start-demo.md` for a repo-only first run
4. this `AGENTS.md` for packet choice, trust boundaries, and workflow posture
5. `docs/v1-validation.md` only when the task is to validate the public wedge claim

Do not start with maintainer-only sibling-repo trial helpers unless the task explicitly calls for that validation style.

## Project Posture

- Dominant milestone question: does the current `v1` wedge beat the formatter-plus-bug-lint stack strongly enough to justify a broader public pre-beta share?
- Current claim boundary: `formsmith` is a repo-first, pre-beta beauty pass for code that already formats cleanly and passes ordinary lint. Keep claims within safe local rewrites, CLJ and CLJS coverage, and benchmark-backed evidence.

## AI Operator Defaults

- Start with the repo-only cold-start demo if sibling benchmark repos are not available.
- Start with `lint` then `fix --check` when the task is ordinary tool use on the current repo.
- Use `baseline` plus `.formsmith.edn` when adopting `formsmith` in an existing
  repo that should fail CI only on new findings.
- Use `bb validate-cold-start` for public first-run path validation.
- Use `bb validate-v1` only when evaluating whether the current public wedge claim is actually proven.
- Treat exit code `2` from `lint` or `fix --check` as "findings present", not as infrastructure failure.
- Keep claims repo-first. Do not imply package installation, editor integration, or broad architecture analysis support.
- Preserve the trust boundary:
  - default `fix` is conservative
  - `--aggressive` is explicit opt-in
  - `--rewrite-only` is for patch-locality inspection, not the default user path

## Recommended Public Packets

### Repo-Only First Run

Use this when proving that a fresh clone can reach meaningful output without sibling repos:

```bash
bash scripts/cold-start-demo.sh prepare
bash scripts/cold-start-demo.sh lint
bash scripts/cold-start-demo.sh preview
```

Optional deeper preview:

```bash
bash scripts/cold-start-demo.sh aggressive-preview
```

### Core Daily Use

Use this when operating `formsmith` on the current repo or another local target:

```bash
clojure -M -m formsmith.main lint src test
clojure -M -m formsmith.main fix --check .
clojure -M -m formsmith.main fix .
clojure -M -m formsmith.main fix --check --aggressive .
clojure -M -m formsmith.main baseline src test -o .formsmith-baseline.edn
```

### Public Claim Validation

Use this only when checking whether the repo's public proof still holds:

```bash
bb validate-v1
bb validate-cold-start
```

Interpretation:

- `bb validate-v1` proves the narrow `v1` wedge against the current benchmark repos
- `bb validate-cold-start` proves the repo-only first-run path still works
- neither packet proves broad external human adoption on its own

## Workflow

- Keep `main` honest for the current wedge. Do not widen public claims ahead of evidence.
- For mergeable work, use short-lived branches and PRs. Packetize changes around one design move or one benchmark-relevant step.
- Run `bb ci` for code changes.
- Also run `bb benchmark-v1` when touching rule behavior, rewrite trust, benchmark docs, or any public product claim.
- Run `bb validate-cold-start` when touching public first-run docs, `scripts/cold-start-demo.sh`, or the bundled demo sources under `examples/cold-start-demo/`.
- Prefer rule-family slices over random isolated rules.
- Add or update corpus fixtures before broadening a rewrite family.
- Treat benchmark regressions as product regressions, not as incidental noise.

## Reporting

- Report at milestone or gate changes, not at individual rule counts.
- Call out any change that materially shifts benchmark counts, fix trust, or the public claim boundary.
