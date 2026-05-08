# Contributing to formsmith

## Current Wedge

`formsmith` is still a pre-beta, repo-first tool.

The current public contract is narrow:

- post-format, post-lint beauty pass
- conservative local rewrites first
- CLJ and CLJS coverage
- explainable safety boundaries

If a contribution needs broader claims than that, it should first tighten the benchmark evidence and docs that justify the change.

## Outside Contribution Posture

Issues are welcome. Pull requests are not the normal contribution path yet.

`formsmith` is still in a narrow pre-beta wedge, and rewrite trust depends on
small maintainer-owned packets with benchmark evidence. Maintainers may close or
redirect broad implementation PRs even when the underlying idea is useful.

If you want to help, open an issue with:

- the code shape you expected `formsmith` to catch
- the Clojure/ClojureScript context
- whether the code already passes formatter and ordinary lint
- the output from `formsmith lint` or `formsmith fix --check` when available

## Maintainer Workflow

1. Use a short-lived branch for each mergeable packet.
2. Open a PR for changes intended to merge when the packet is larger than an urgent repair.
3. Keep `main` green and truthfully representative of the current wedge.

## Required Checks

- Run `bb ci` for code changes.
- Run `bb benchmark-v1` when changing:
  - rule behavior
  - rewrite trust or patch locality
  - benchmark docs or numbers
  - public product claims in `README.md` or `docs/`
- Run `bb validate-cold-start` when changing:
  - `README.md`, `docs/quickstart.md`, or other public first-run docs
  - `docs/cold-start-demo.md` or `docs/cold-start-adoption.md`
  - `scripts/cold-start-demo.sh` or the bundled demo sources under `examples/cold-start-demo/`

## Rule Work

- Prefer one rule family or one trust improvement per packet.
- Add corpus coverage before widening a rewrite family.
- Use protected corpus cases when comments or local layout could regress.
- Verify new signal on at least one benchmark repo before widening the public claim.

## Support Posture

`formsmith` is best-effort while the `v1` wedge is still being proven.

- no stability guarantees yet
- issue scope may be narrowed to the active wedge
- fixes that widen the claim boundary should come with benchmark evidence

## Distribution Posture

`formsmith` is source-distributed while it is pre-beta.

- use the GitHub source release or a pinned git SHA from Clojure CLI
- no Clojars package is published yet
- no editor extension or standalone binary is published yet
- do not document distribution flows that the repo does not actually support
