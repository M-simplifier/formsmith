# Review Study: <repository>

## Repository

- repository: <https://github.com/org/repo>
- ownership: independent | controlled-adopter | maintainer-pressure
- baseline commit: `<sha>`
- measured commit or PR range: <https://github.com/org/repo/pull/000>
- Formsmith version: `v0.1.0-pre.4`
- Formsmith SHA: `a2449935781fc6f3b2b4451aa3e8e30e0bbc9abf`

## Study Boundary

- target paths: `src test`
- ordinary gates: formatter, clj-kondo, tests, build
- Formsmith gate: `clojure -M:formsmith check src test`
- applied mode: default | guarded | aggressive-preview | contract-only
- ignored paths: `target`, generated assets, vendored code

## Public Evidence

- adoption PR: <https://github.com/org/repo/pull/000>
- PR CI: <https://github.com/org/repo/actions/runs/000>
- main CI after merge: <https://github.com/org/repo/actions/runs/000>
- review sample before Formsmith: <https://github.com/org/repo/pull/000>
- review sample after Formsmith: <https://github.com/org/repo/pull/000>

## Finding Summary

```text
before_check=files=0 changed=0 findings=0
after_check=files=0 changed=0 findings=0
aggressive_preview=files=0 changed=0 findings=0
contracts=0
```

## Review Comment Classification

| Classification | Count | Notes |
| --- | ---: | --- |
| eliminated-by-formsmith | 0 | |
| missed-covered-rule | 0 | |
| false-positive | 0 | |
| accepted-suppression | 0 | |
| out-of-scope | 0 | |
| unclear | 0 | |

## Suppressions And False Positives

List every suppression or false positive with the file, rule id, reason, and
public discussion link.

## Failures

List semantic regressions, rejected default fixes, parser crashes, CI failures,
or state `none`.

## Verdict

State whether this packet counts as maintainer-pressure, controlled-adopter,
independent-adoption, or does not count. Do not call the result L5 unless it is
part of a larger multi-repo public study that meets
[`docs/review-elimination-study.md`](../review-elimination-study.md).
