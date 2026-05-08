# Field-Trial Evidence: 2026-05-08

This directory contains the public evidence snapshot for the pinned open-source
field trial documented in [`docs/field-trials.md`](../../../field-trials.md).

Files:

- `summary.tsv`
  Per-repository file, changed-file, and finding counts.
- `repo-rule-summary.tsv`
  Per-repository rule counts.
- `rule-summary.tsv`
  Cross-repository rule counts.
- `reports/*.txt`
  Per-finding reports with repository-relative paths, line, column, rule id,
  message, and safety tier.

The reports intentionally do not commit third-party source diffs. They provide
enough public coordinates to inspect each finding in the pinned upstream repo.
Generate local diffs by rerunning:

```bash
bb field-trial
```

Then apply `formsmith fix --rewrite-only` in the cloned target under
`target/field-trials/work/<repo>` if a patch-level adjudication is needed.
