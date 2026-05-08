# Review Elimination Study

## Purpose

This document defines the public evidence needed before Formsmith can claim that
it removes code-level review work, not merely that it finds plausible style
issues.

The study question is:

> after Formsmith is installed and enforced, do reviewers stop leaving comments
> about the code shapes Formsmith claims to own?

This is stricter than a field trial. A field trial proves parser survival and
measurable signal. A review-elimination study needs adoption, CI enforcement,
review history, and adjudicated false positives.

## Scope

Count only code-level review comments that fall inside a documented Formsmith
rule, safety tier, or LLM refactor contract.

In scope:

- local idiom comments such as `when-not`, `if-not`, `nil?`, `some?`, keyword
  lookup, redundant wrappers, and supported `seq` forms
- comments that ask for a change equivalent to a current Formsmith autofix
- comments that ask for a change equivalent to a current Formsmith contract

Out of scope:

- product behavior, domain correctness, security, performance, data modeling,
  naming, tests, architecture, and framework design comments
- style preferences that are not represented by a Formsmith rule or contract
- comments on generated code or intentionally ignored paths

Out-of-scope comments are not failures. They must remain visible so the claim
does not silently widen beyond the implemented tool.

## Packet

A valid public study packet records:

| Field | Requirement |
| --- | --- |
| repository | public repo URL |
| ownership | independent, controlled adopter, or maintainer pressure project |
| baseline commit | commit before Formsmith adoption or before the measured PR set |
| Formsmith version | release tag and SHA |
| target paths | exact paths checked in CI |
| ordinary gates | formatter, lint, tests, and build checks used by the repo |
| Formsmith gate | `check` command used by CI |
| applied mode | default, guarded, aggressive preview, or contract-only |
| findings before | file, changed-file, and finding counts before adoption |
| findings after | same counts after autofix or baseline |
| suppressions | count and reason for every suppression |
| review sample | PR URLs used for before/after review comparison |
| classification | counts by the taxonomy below |
| failures | semantic regressions, rejected fixes, or CI failures |

Use [`docs/review-studies/template.md`](./review-studies/template.md) for new
records. Completed records can be checked with:

```bash
REVIEW_STUDY=docs/review-studies/<record>.md bb verify-review-study
```

## Classification

Every relevant review comment in the sample should be classified as one of:

- `eliminated-by-formsmith`
  The comment would have been prevented by the installed Formsmith gate.
- `missed-covered-rule`
  The comment is inside an existing Formsmith rule or contract, but the tool did
  not report it.
- `false-positive`
  Formsmith reported a finding that maintainers rejected as undesirable.
- `accepted-suppression`
  The repo intentionally suppresses the finding and documents why.
- `out-of-scope`
  The comment is legitimate review work outside Formsmith's current claim.
- `unclear`
  The evidence is insufficient; do not count it as success.

## Pass Bar For An L5 Claim

Formsmith should not publicly claim L5 until public packets show all of these:

- at least three independent public repositories or teams use a released
  Formsmith coordinate in CI
- at least two of those repositories have accepted either default autofixes or a
  baseline-plus-CI gate on `main`
- zero known semantic regressions from default autofixes in the measured packet
- no unresolved parser crashes on the measured target paths
- covered code-level review comments are eliminated or blocked before review at
  a high enough rate that reviewers no longer need to inspect those shapes
- suppressions and false positives are low, explained, and stable across
  releases
- all remaining review comments are explicitly classified as out of scope,
  missed coverage, or unclear rather than hidden

Until that evidence exists, the honest claim is lower: Formsmith can be a useful
pre-beta beauty pass with controlled adoption and public field-trial evidence,
but it has not yet proven broad review-comment elimination.

## Minimal Command Set

For an adopter repo with a `:formsmith` alias:

```bash
clojure -M:formsmith check src test
clojure -M:formsmith fix --check src test
clojure -M:formsmith fix --check --aggressive src test
clojure -M:formsmith contracts --json src test > target/formsmith-contracts.json
```

For the Formsmith repo's public field-trial packet:

```bash
bb field-trial
bb summarize-field-trial
```

## Current State

As of `v0.1.0-pre.6`, Formsmith has:

- public release-coordinate adoption in maintainer-controlled pressure projects
- public CI evidence for those projects
- open-source field-trial scans across eight public repos
- machine-readable field-trial summary tables
- a public [L5 scorecard](./l5-scorecard.md) that separates the current
  evidence dimensions from the missing review-elimination claim

It does not yet have independent public adoption or a completed
review-elimination study.
