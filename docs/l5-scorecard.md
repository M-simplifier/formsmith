# L5 Scorecard

## Current Level

Current public level: strong L3.

Formsmith has public release, CI, controlled adoption, pressure projects, and
open-source field-trial evidence. It does not yet have independent public
review-comment elimination evidence.

## Dimensions

| Dimension | Current State | Public Evidence | L5 Status |
| --- | --- | --- | --- |
| Source release | `v0.1.0-pre.6` is documented and consumed by controlled adopters | `docs/install.md`, release notes, adoption records | pass |
| Public CI | Formsmith public main CI and Pages are green | GitHub Actions main runs | pass |
| Repo-only first run | Fresh clone can run the cold-start demo | `bb validate-cold-start` and site evidence | pass |
| Controlled adoption | `clj-ops-ledger` adopted the released coordinate in CI | `docs/adoptions/clj-ops-ledger.md` | pass for controlled evidence |
| Maintainer pressure | `formsmith-pressure-app` consumes the released coordinate in CI | `docs/adoptions/formsmith-pressure-app.md` | pass for pressure evidence |
| Field-trial parser survival | 8 public repos, 601 files, 111 findings, no unresolved crash | `docs/evidence/field-trials/2026-05-08/` | pass for outside-pressure scans |
| False-positive adjudication | No upstream maintainer adjudication yet | current records note no suppressions, but no external review | not passed |
| Independent adoption | No independent public repo/team has accepted Formsmith in CI yet | none | not passed |
| Review-comment elimination | Current review-study records have `eliminated-by-formsmith = 0` | `docs/review-studies/` | not passed |
| Cross-release stability | Evidence covers only the current pre-beta sequence | release notes and adoption records | not passed |

## L5 Bar

Do not claim L5 until all of these are true:

- at least three independent public repos or teams use a released Formsmith
  coordinate in CI
- at least two independent repos accept default autofixes or baseline-plus-CI on
  `main`
- public before/after review samples show covered comments eliminated or
  blocked before review
- false positives, suppressions, rejected fixes, and semantic regressions are
  publicly adjudicated
- the evidence remains stable across releases

## Next External Gate

The next step toward L4/L5 is an independent adoption or maintainer-adjudication
packet against a public repository with low-risk findings that a maintainer can
inspect. Target selection is maintainer-operated; public evidence should be
recorded only after an upstream action is opened or completed.
